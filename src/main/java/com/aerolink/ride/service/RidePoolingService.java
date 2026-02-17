package com.aerolink.ride.service;

import com.aerolink.ride.algorithm.RideGroupingAlgorithm;
import com.aerolink.ride.algorithm.RouteDeviationChecker;
import com.aerolink.ride.dto.event.RideEvent;
import com.aerolink.ride.dto.request.RideRequestDTO;
import com.aerolink.ride.dto.response.PoolResponseDTO;
import com.aerolink.ride.dto.response.RideResponseDTO;
import com.aerolink.ride.entity.*;
import com.aerolink.ride.enums.*;
import com.aerolink.ride.exception.*;
import com.aerolink.ride.messaging.RideEventPublisher;
import com.aerolink.ride.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RidePoolingService {

    private final RideRequestRepository rideRequestRepository;
    private final RidePoolRepository ridePoolRepository;
    private final PassengerRepository passengerRepository;
    private final PricingService pricingService;
    private final RideEventPublisher eventPublisher;
    private final RedisLockRegistry redisLockRegistry;
    private final SseService sseService;

    @Value("${aerolink.pooling.search-radius-km}")
    private double searchRadiusKm;

    @Value("${aerolink.pooling.max-pool-size}")
    private int maxPoolSize;

    @Value("${aerolink.pooling.pool-window-seconds}")
    private int poolWindowSeconds;

    @Value("${aerolink.concurrency.lock-timeout-seconds}")
    private long lockTimeoutSeconds;

    @Value("${aerolink.concurrency.optimistic-retry-max}")
    private int maxRetries;

    /**
     * Process a ride request (batch pooling model):
     * 1. Idempotency check
     * 2. One-ride-per-user validation
     * 3. Find/create FORMING pool (NO cab assigned, NO price calculated)
     * 4. Rider sees "waiting for pool" until dispatch
     */
    @Transactional
    public RideResponseDTO requestRide(RideRequestDTO dto) {
        log.info("Ride request received: passenger={}, passengers={}, luggage={}, pickup=({},{}), drop=({},{})",
                dto.getPassengerId(), dto.getPassengerCount(), dto.getLuggageCount(),
                dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng());

        // 1. Idempotency check
        var existing = rideRequestRepository.findByIdempotencyKey(dto.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate request detected for idempotency key: {}", dto.getIdempotencyKey());
            return toResponse(existing.get());
        }

        // 2. Validate passenger exists
        Passenger passenger = passengerRepository.findById(dto.getPassengerId())
                .orElseThrow(() -> new ResourceNotFoundException("Passenger not found: " + dto.getPassengerId()));

        // 3. One-ride-per-user: prevent booking if active ride exists
        if (rideRequestRepository.hasActiveRide(dto.getPassengerId())) {
            throw new InvalidOperationException(
                    "Passenger already has an active ride. Complete or cancel it before booking a new one.");
        }

        // 4. Validate capacity constraints (fixed 4 seats / 4 luggage)
        if (dto.getPassengerCount() > 4) {
            throw new InvalidOperationException("Maximum 4 passengers per ride request");
        }
        if (dto.getLuggageCount() > 4) {
            throw new InvalidOperationException("Maximum 4 luggage items per ride request");
        }

        // 5. Create ride request entity
        RideRequest rideRequest = RideRequest.builder()
                .passenger(passenger)
                .pickupLat(dto.getPickupLat())
                .pickupLng(dto.getPickupLng())
                .dropLat(dto.getDropLat())
                .dropLng(dto.getDropLng())
                .passengerCount(dto.getPassengerCount())
                .luggageCount(dto.getLuggageCount())
                .maxDetourKm(dto.getMaxDetourKm())
                .status(RideStatus.PENDING)
                .idempotencyKey(dto.getIdempotencyKey())
                .build();
        rideRequest = rideRequestRepository.save(rideRequest);

        // 6. Find or create a FORMING pool (NO cab assignment, NO pricing yet)
        RidePool assignedPool = findOrCreatePool(rideRequest);

        // 7. Save final state
        rideRequest = rideRequestRepository.save(rideRequest);

        // 8. Publish event + SSE notification
        eventPublisher.publishNotification(
                RideEvent.pooled(rideRequest.getId(), assignedPool.getId(), passenger.getId()));

        // Notify all riders in this pool about the new join
        sseService.emitToPool(assignedPool.getId(), "POOL_JOINED",
                "New rider joined pool. " + assignedPool.getTotalOccupiedSeats() + "/4 seats filled.");

        log.info("Ride request {} joined pool {} ({}/4 seats)",
                rideRequest.getId(), assignedPool.getId(), assignedPool.getTotalOccupiedSeats());
        return toResponse(rideRequest);
    }

    /**
     * Find the best existing FORMING pool or create a new one.
     * Key difference from v1: NO cab is assigned here. Cab assignment happens at
     * dispatch.
     */
    private RidePool findOrCreatePool(RideRequest rideRequest) {
        // 1. Search for FORMING pools near the pickup location
        List<RidePool> candidates = ridePoolRepository.findFormingPoolsNear(
                rideRequest.getPickupLat(), rideRequest.getPickupLng(), searchRadiusKm);

        log.info("Step 1: Found {} FORMING pools near ({}, {})",
                candidates.size(), rideRequest.getPickupLat(), rideRequest.getPickupLng());

        // Score and find best pool
        RidePool bestPool = RideGroupingAlgorithm.findBestPool(candidates, rideRequest, maxPoolSize);

        if (bestPool != null) {
            log.info("Joining existing pool {}", bestPool.getId());
            return addToPoolWithLock(bestPool, rideRequest);
        }

        // 2. No suitable pool → create a new one with a 60s window
        log.info("No suitable pool found. Creating new pool with {}s window.", poolWindowSeconds);
        return createNewPool(rideRequest);
    }

    /**
     * Create a new pool anchored at this rider's pickup/drop.
     * NO cab assigned — cab is found at dispatch time.
     */
    private RidePool createNewPool(RideRequest rideRequest) {
        RidePool pool = RidePool.builder()
                .status(PoolStatus.FORMING)
                .totalOccupiedSeats(rideRequest.getPassengerCount())
                .totalLuggage(rideRequest.getLuggageCount())
                .totalRouteDistanceKm(RouteDeviationChecker.estimateTotalRouteDistance(List.of(), rideRequest))
                .pickupLat(rideRequest.getPickupLat())
                .pickupLng(rideRequest.getPickupLng())
                .dropLat(rideRequest.getDropLat())
                .dropLng(rideRequest.getDropLng())
                .windowExpiresAt(LocalDateTime.now().plusSeconds(poolWindowSeconds))
                .build();
        pool = ridePoolRepository.save(pool);

        rideRequest.setRidePool(pool);
        rideRequest.setStatus(RideStatus.POOLED);

        log.info("Created new pool {} with window expiring at {}", pool.getId(), pool.getWindowExpiresAt());
        return pool;
    }

    /**
     * Add a ride request to an existing pool with distributed locking.
     */
    private RidePool addToPoolWithLock(RidePool pool, RideRequest rideRequest) {
        String lockKey = "pool:" + pool.getId();
        Lock lock = redisLockRegistry.obtain(lockKey);

        try {
            if (!lock.tryLock(lockTimeoutSeconds, TimeUnit.SECONDS)) {
                throw new ConcurrencyConflictException("Could not acquire lock for pool: " + pool.getId());
            }

            try {
                return addToPoolWithRetry(pool.getId(), rideRequest);
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConcurrencyConflictException("Lock acquisition interrupted for pool: " + pool.getId());
        }
    }

    /**
     * Add to pool with optimistic locking retry.
     */
    private RidePool addToPoolWithRetry(UUID poolId, RideRequest rideRequest) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                RidePool pool = ridePoolRepository.findById(poolId)
                        .orElseThrow(() -> new ResourceNotFoundException("Pool not found: " + poolId));

                // Re-validate: pool must still be FORMING
                if (pool.getStatus() != PoolStatus.FORMING) {
                    log.info("Pool {} is no longer FORMING (status={}), creating new pool", poolId, pool.getStatus());
                    return createNewPool(rideRequest);
                }

                // Re-validate capacity
                if (!RideGroupingAlgorithm.hasCapacity(pool, rideRequest, maxPoolSize)) {
                    log.info("Pool {} no longer has capacity, creating new pool", poolId);
                    return createNewPool(rideRequest);
                }

                pool.setTotalOccupiedSeats(pool.getTotalOccupiedSeats() + rideRequest.getPassengerCount());
                pool.setTotalLuggage(pool.getTotalLuggage() + rideRequest.getLuggageCount());

                List<RideRequest> activeRequests = rideRequestRepository.findActiveRequestsByPoolId(poolId);
                pool.setTotalRouteDistanceKm(
                        RouteDeviationChecker.estimateTotalRouteDistance(activeRequests, rideRequest));

                pool = ridePoolRepository.save(pool);

                rideRequest.setRidePool(pool);
                rideRequest.setStatus(RideStatus.POOLED);

                return pool;

            } catch (OptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict on pool {} (attempt {}/{})", poolId, attempt, maxRetries);
                if (attempt == maxRetries) {
                    throw new ConcurrencyConflictException("Failed to add to pool after " + maxRetries + " retries");
                }
            }
        }
        throw new ConcurrencyConflictException("Failed to add to pool");
    }

    // === Read Operations ===

    @Transactional(readOnly = true)
    public List<RideResponseDTO> getAllRides() {
        return rideRequestRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RideResponseDTO getRide(UUID rideId) {
        RideRequest request = rideRequestRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + rideId));
        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public PoolResponseDTO getPool(UUID poolId) {
        RidePool pool = ridePoolRepository.findById(poolId)
                .orElseThrow(() -> new ResourceNotFoundException("Pool not found: " + poolId));

        List<RideRequest> members = rideRequestRepository.findActiveRequestsByPoolId(poolId);

        return PoolResponseDTO.builder()
                .id(pool.getId())
                .cabId(pool.getCab() != null ? pool.getCab().getId() : null)
                .cabLicensePlate(pool.getCab() != null ? pool.getCab().getLicensePlate() : null)
                .driverName(pool.getCab() != null ? pool.getCab().getDriverName() : null)
                .status(pool.getStatus())
                .totalOccupiedSeats(pool.getTotalOccupiedSeats())
                .totalLuggage(pool.getTotalLuggage())
                .remainingSeats(pool.getRemainingSeats())
                .totalRouteDistanceKm(pool.getTotalRouteDistanceKm())
                .windowExpiresAt(pool.getWindowExpiresAt())
                .riders(members.stream().map(this::toResponse).collect(Collectors.toList()))
                .createdAt(pool.getCreatedAt())
                .build();
    }

    /**
     * Get passenger dashboard data: active ride + ride history.
     */
    @Transactional(readOnly = true)
    public PassengerDashboardData getPassengerDashboard(UUID passengerId) {
        passengerRepository.findById(passengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger not found: " + passengerId));

        List<RideRequest> activeRides = rideRequestRepository.findActiveRidesByPassengerId(passengerId);
        RideRequest activeRide = activeRides.isEmpty() ? null : activeRides.get(0);
        List<RideRequest> history = rideRequestRepository.findByPassengerIdOrderByCreatedAtDesc(passengerId);

        RideResponseDTO activeDto = activeRide != null ? toResponse(activeRide) : null;
        PoolResponseDTO activePoolDto = null;

        if (activeRide != null && activeRide.getRidePool() != null) {
            RidePool pool = activeRide.getRidePool();
            List<RideRequest> poolMembers = rideRequestRepository.findActiveRequestsByPoolId(pool.getId());
            activePoolDto = PoolResponseDTO.builder()
                    .id(pool.getId())
                    .cabId(pool.getCab() != null ? pool.getCab().getId() : null)
                    .cabLicensePlate(pool.getCab() != null ? pool.getCab().getLicensePlate() : null)
                    .driverName(pool.getCab() != null ? pool.getCab().getDriverName() : null)
                    .status(pool.getStatus())
                    .totalOccupiedSeats(pool.getTotalOccupiedSeats())
                    .totalLuggage(pool.getTotalLuggage())
                    .remainingSeats(pool.getRemainingSeats())
                    .totalRouteDistanceKm(pool.getTotalRouteDistanceKm())
                    .windowExpiresAt(pool.getWindowExpiresAt())
                    .riders(poolMembers.stream().map(this::toResponse).collect(Collectors.toList()))
                    .createdAt(pool.getCreatedAt())
                    .build();
        }

        List<RideResponseDTO> historyDtos = history.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return new PassengerDashboardData(activeDto, activePoolDto, historyDtos);
    }

    /**
     * Passenger dashboard data container.
     */
    public record PassengerDashboardData(
            RideResponseDTO activeRide,
            PoolResponseDTO activePool,
            List<RideResponseDTO> rideHistory) {
    }

    /**
     * Get driver dashboard: active pool with all riders.
     */
    @Transactional(readOnly = true)
    public DriverDashboardData getDriverDashboard(UUID cabId) {
        List<RidePool> activePools = ridePoolRepository.findActivePoolsByCabId(cabId);

        if (activePools.isEmpty()) {
            return new DriverDashboardData(null, List.of());
        }

        RidePool activePool = activePools.get(0);

        List<RideRequest> riders = rideRequestRepository.findActiveRequestsByPoolId(activePool.getId());
        PoolResponseDTO poolDto = PoolResponseDTO.builder()
                .id(activePool.getId())
                .cabId(cabId)
                .status(activePool.getStatus())
                .totalOccupiedSeats(activePool.getTotalOccupiedSeats())
                .totalLuggage(activePool.getTotalLuggage())
                .remainingSeats(activePool.getRemainingSeats())
                .totalRouteDistanceKm(activePool.getTotalRouteDistanceKm())
                .riders(riders.stream().map(this::toResponse).collect(Collectors.toList()))
                .createdAt(activePool.getCreatedAt())
                .build();

        return new DriverDashboardData(poolDto, riders.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    public record DriverDashboardData(
            PoolResponseDTO activePool,
            List<RideResponseDTO> riders) {
    }

    // === DTO Mapping ===

    private RideResponseDTO toResponse(RideRequest request) {
        return RideResponseDTO.builder()
                .id(request.getId())
                .passengerId(request.getPassenger().getId())
                .passengerName(request.getPassenger().getName())
                .pickupLat(request.getPickupLat())
                .pickupLng(request.getPickupLng())
                .dropLat(request.getDropLat())
                .dropLng(request.getDropLng())
                .passengerCount(request.getPassengerCount())
                .luggageCount(request.getLuggageCount())
                .status(request.getStatus())
                .ridePoolId(request.getRidePool() != null ? request.getRidePool().getId() : null)
                .estimatedPrice(request.getEstimatedPrice())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
