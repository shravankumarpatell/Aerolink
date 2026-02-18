package com.aerolink.ride.service;

import com.aerolink.ride.algorithm.RouteDeviationChecker;
import com.aerolink.ride.dto.event.RideEvent;
import com.aerolink.ride.entity.Cab;
import com.aerolink.ride.entity.RidePool;
import com.aerolink.ride.entity.RideRequest;
import com.aerolink.ride.enums.CabStatus;
import com.aerolink.ride.enums.PoolStatus;
import com.aerolink.ride.enums.RideStatus;
import com.aerolink.ride.exception.InvalidOperationException;
import com.aerolink.ride.exception.ResourceNotFoundException;
import com.aerolink.ride.messaging.RideEventPublisher;
import com.aerolink.ride.repository.CabRepository;
import com.aerolink.ride.repository.RidePoolRepository;
import com.aerolink.ride.repository.RideRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationService {

    private final RideRequestRepository rideRequestRepository;
    private final RidePoolRepository ridePoolRepository;
    private final CabRepository cabRepository;
    private final PricingService pricingService;
    private final RideEventPublisher eventPublisher;
    private final RedisLockRegistry redisLockRegistry;
    private final SseService sseService;

    /**
     * Cancel a ride request.
     * Handles both pre-dispatch (FORMING) and post-dispatch
     * (CONFIRMED/IN_PROGRESS).
     */
    @Transactional
    public void cancelRide(UUID rideRequestId, String reason) {
        RideRequest request = rideRequestRepository.findById(rideRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + rideRequestId));

        // Validate cancellation is allowed
        if (request.getStatus() == RideStatus.COMPLETED || request.getStatus() == RideStatus.CANCELLED) {
            throw new InvalidOperationException(
                    "Cannot cancel ride in " + request.getStatus() + " status");
        }

        UUID poolId = request.getRidePool() != null ? request.getRidePool().getId() : null;

        // Mark as cancelled
        request.setStatus(RideStatus.CANCELLED);
        rideRequestRepository.save(request);

        log.info("Ride {} cancelled. Reason: {}", rideRequestId, reason);

        // Handle pool update if the ride was pooled
        if (poolId != null) {
            handlePoolUpdate(poolId, request);
        }

        // SSE notification to the cancelling passenger
        sseService.emitToPassenger(request.getPassenger().getId(), "RIDE_CANCELLED",
                "Your ride has been cancelled: " + reason);
        sseService.removePassengerFromPool(poolId, request.getPassenger().getId());

        // Publish cancellation event
        UUID passengerId = request.getPassenger().getId();
        eventPublisher.publishCancellation(
                RideEvent.cancellation(rideRequestId, poolId, passengerId, reason));
    }

    /**
     * Update pool after a cancellation. Uses distributed lock on the pool.
     */
    private void handlePoolUpdate(UUID poolId, RideRequest cancelledRequest) {
        String lockKey = "pool:" + poolId;
        Lock lock = redisLockRegistry.obtain(lockKey);

        try {
            if (!lock.tryLock(10, TimeUnit.SECONDS)) {
                throw new RuntimeException(
                        "Could not acquire lock for pool " + poolId + " during cancellation. Please retry.");
            }

            try {
                RidePool pool = ridePoolRepository.findById(poolId)
                        .orElseThrow(() -> new ResourceNotFoundException("Pool not found: " + poolId));

                // Update pool counts
                pool.setTotalOccupiedSeats(
                        Math.max(0, pool.getTotalOccupiedSeats() - cancelledRequest.getPassengerCount()));
                pool.setTotalLuggage(
                        Math.max(0, pool.getTotalLuggage() - cancelledRequest.getLuggageCount()));

                // Check remaining active riders
                List<RideRequest> activeRiders = rideRequestRepository.findActiveRequestsByPoolId(poolId);

                if (activeRiders.isEmpty()) {
                    // Dissolve pool and release cab
                    dissolvePool(pool);
                } else {
                    // Recalculate route distance with remaining riders
                    if (activeRiders.size() == 1) {
                        RideRequest sole = activeRiders.get(0);
                        pool.setTotalRouteDistanceKm(
                                RouteDeviationChecker.estimateTotalRouteDistance(List.of(), sole));
                    } else {
                        // Use first rider as "new" and rest as "existing" for the algorithm
                        RideRequest first = activeRiders.get(0);
                        List<RideRequest> rest = activeRiders.subList(1, activeRiders.size());
                        pool.setTotalRouteDistanceKm(
                                RouteDeviationChecker.estimateTotalRouteDistance(rest, first));
                    }

                    ridePoolRepository.save(pool);

                    // Only recalculate prices if pool was already dispatched (has prices)
                    if (pool.getStatus() == PoolStatus.CONFIRMED || pool.getStatus() == PoolStatus.IN_PROGRESS) {
                        pricingService.recalculatePoolPricing(activeRiders,
                                activeRiders.stream().mapToInt(RideRequest::getPassengerCount).sum());

                        // Notify remaining riders about price update
                        for (RideRequest rider : activeRiders) {
                            sseService.emitToPassenger(rider.getPassenger().getId(), "PRICE_UPDATED",
                                    "A rider cancelled. Your fare has been recalculated.");
                        }
                    }

                    // Notify remaining riders about the cancellation
                    sseService.emitToPool(poolId, "RIDER_CANCELLED",
                            "A rider left the pool. " + activeRiders.size() + " riders remaining.");

                    log.info("Pool {} updated: {} riders remaining", poolId, activeRiders.size());
                }
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for pool {}", poolId);
        }
    }

    /**
     * Dissolve a pool and release the assigned cab.
     */
    private void dissolvePool(RidePool pool) {
        pool.setStatus(PoolStatus.DISSOLVED);
        pool.setTotalOccupiedSeats(0);
        pool.setTotalLuggage(0);
        ridePoolRepository.save(pool);

        // Release the cab back to AVAILABLE (if one was assigned)
        if (pool.getCab() != null) {
            Cab cab = pool.getCab();
            cab.setStatus(CabStatus.AVAILABLE);
            cabRepository.save(cab);

            // Notify driver that trip was cancelled
            sseService.emitToDriver(cab.getId(), "TRIP_CANCELLED",
                    "All riders cancelled. Trip dissolved.");

            log.info("Cab {} released from dissolved pool {}", cab.getId(), pool.getId());
        }

        sseService.cleanupPool(pool.getId());
        eventPublisher.publishNotification(RideEvent.poolDissolved(pool.getId()));
        log.info("Pool {} dissolved", pool.getId());
    }
}
