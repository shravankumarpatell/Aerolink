package com.aerolink.ride.service;

import com.aerolink.ride.entity.Cab;
import com.aerolink.ride.entity.RidePool;
import com.aerolink.ride.entity.RideRequest;
import com.aerolink.ride.enums.CabStatus;
import com.aerolink.ride.enums.PoolStatus;
import com.aerolink.ride.enums.RideStatus;
import com.aerolink.ride.repository.CabRepository;
import com.aerolink.ride.repository.RidePoolRepository;
import com.aerolink.ride.repository.RideRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scheduler that runs every 5 seconds to dispatch pools that are ready.
 * A pool is ready when: it's full (4 riders) or its 60s window has expired.
 *
 * Dispatch process:
 * 1. Find nearest AVAILABLE cab
 * 2. Assign cab to pool
 * 3. Calculate final fares for all riders based on actual pool size
 * 4. Update statuses to CONFIRMED
 * 5. Send SSE notifications to all riders and the driver
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PoolDispatchScheduler {

    private final RidePoolRepository ridePoolRepository;
    private final RideRequestRepository rideRequestRepository;
    private final CabRepository cabRepository;
    private final PricingService pricingService;
    private final SseService sseService;
    private final TransactionTemplate transactionTemplate;

    @Value("${aerolink.pooling.search-radius-km}")
    private double searchRadiusKm;

    private volatile boolean cleanupDone = false;

    /**
     * On startup, clean up any stale FORMING/DISPATCHING pools left over
     * from previous crashed sessions. This prevents the scheduler from
     * repeatedly failing on broken data.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupStalePools() {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                List<RidePool> stalePools = ridePoolRepository.findStaleFormingOrDispatchingPools();
                if (stalePools.isEmpty()) {
                    log.info("Startup cleanup: no stale pools found");
                    return;
                }

                log.warn("Startup cleanup: found {} stale FORMING/DISPATCHING pools, dissolving...", stalePools.size());
                for (RidePool pool : stalePools) {
                    List<RideRequest> riders = rideRequestRepository.findActiveRequestsByPoolId(pool.getId());
                    for (RideRequest rider : riders) {
                        rider.setStatus(RideStatus.CANCELLED);
                        rideRequestRepository.save(rider);
                    }

                    if (pool.getCab() != null) {
                        pool.getCab().setStatus(CabStatus.AVAILABLE);
                        cabRepository.save(pool.getCab());
                    }

                    pool.setStatus(PoolStatus.DISSOLVED);
                    ridePoolRepository.save(pool);
                    log.info("Dissolved stale pool {} ({} riders cancelled)", pool.getId(), riders.size());
                }

                List<Cab> assignedCabs = cabRepository.findByStatus(CabStatus.ASSIGNED);
                for (Cab cab : assignedCabs) {
                    List<RidePool> activePools = ridePoolRepository.findActivePoolsByCabId(cab.getId());
                    if (activePools.isEmpty()) {
                        cab.setStatus(CabStatus.AVAILABLE);
                        cabRepository.save(cab);
                        log.info("Released orphaned ASSIGNED cab: {} ({})", cab.getDriverName(), cab.getLicensePlate());
                    }
                }
            });
        } catch (Exception e) {
            log.error("Startup cleanup failed (non-fatal): {}", e.getMessage());
        } finally {
            cleanupDone = true;
            log.info("Startup cleanup complete — scheduler now active");
        }
    }

    @Scheduled(fixedRateString = "${aerolink.pooling.dispatch-interval-ms}")
    public void dispatchReadyPools() {
        if (!cleanupDone) {
            return; // Skip until startup cleanup has finished
        }

        // Wrap the read query in a transaction (LEFT JOIN FETCH needs it)
        List<RidePool> readyPools = transactionTemplate
                .execute(status -> ridePoolRepository.findPoolsReadyForDispatch(LocalDateTime.now()));

        if (readyPools == null || readyPools.isEmpty()) {
            return;
        }

        log.info("Dispatch scheduler: found {} pools ready for dispatch", readyPools.size());

        for (RidePool pool : readyPools) {
            try {
                dispatchPool(pool.getId());
            } catch (Exception e) {
                log.error("Failed to dispatch pool {}: {}", pool.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Dispatch a single pool.
     * Uses TransactionTemplate instead of @Transactional because this method
     * is called from within the same class (self-invocation), which bypasses
     * Spring's AOP proxy and ignores @Transactional annotations.
     */
    public void dispatchPool(java.util.UUID poolId) {
        transactionTemplate.executeWithoutResult(status -> {
            // 1. Acquire pessimistic lock on pool
            RidePool pool = ridePoolRepository.findByIdWithLock(poolId).orElse(null);
            if (pool == null || pool.getStatus() != PoolStatus.FORMING) {
                log.debug("Pool {} already dispatched or not found, skipping", poolId);
                return;
            }

            // 2. Get active riders
            List<RideRequest> activeRiders = rideRequestRepository.findActiveRequestsByPoolId(poolId);
            if (activeRiders.isEmpty()) {
                // No riders — dissolve
                pool.setStatus(PoolStatus.DISSOLVED);
                ridePoolRepository.save(pool);
                sseService.cleanupPool(poolId);
                log.info("Pool {} dissolved — no active riders at dispatch time", poolId);
                return;
            }

            // poolSize = total passengers across all bookings (not number of bookings)
            // e.g. 2 bookings of 2 passengers each = poolSize 4
            int totalPassengers = activeRiders.stream().mapToInt(RideRequest::getPassengerCount).sum();
            log.info("Dispatching pool {} with {} bookings, {} total passengers", poolId, activeRiders.size(),
                    totalPassengers);

            // 3. Set pool to DISPATCHING to prevent new joins
            pool.setStatus(PoolStatus.DISPATCHING);
            ridePoolRepository.save(pool);

            // 4. Find nearest AVAILABLE cab
            List<Cab> availableCabs = cabRepository.findAvailableCabsNear(
                    pool.getPickupLat(), pool.getPickupLng(), searchRadiusKm, CabStatus.AVAILABLE);

            Cab assignedCab = null;
            for (Cab candidate : availableCabs) {
                try {
                    Cab cab = cabRepository.findByIdWithLock(candidate.getId()).orElse(null);
                    if (cab != null && cab.getStatus() == CabStatus.AVAILABLE) {
                        cab.setStatus(CabStatus.ASSIGNED);
                        assignedCab = cabRepository.save(cab);
                        break;
                    }
                } catch (Exception e) {
                    log.debug("Cab {} already taken, trying next", candidate.getId());
                }
            }

            if (assignedCab == null) {
                // No cabs available — expand search radius
                availableCabs = cabRepository.findAvailableCabsNear(
                        pool.getPickupLat(), pool.getPickupLng(), searchRadiusKm * 3, CabStatus.AVAILABLE);

                for (Cab candidate : availableCabs) {
                    try {
                        Cab cab = cabRepository.findByIdWithLock(candidate.getId()).orElse(null);
                        if (cab != null && cab.getStatus() == CabStatus.AVAILABLE) {
                            cab.setStatus(CabStatus.ASSIGNED);
                            assignedCab = cabRepository.save(cab);
                            break;
                        }
                    } catch (Exception e) {
                        log.debug("Cab {} already taken, trying next", candidate.getId());
                    }
                }
            }

            if (assignedCab == null) {
                // Still no cab — keep pool in DISPATCHING, will retry next cycle
                pool.setStatus(PoolStatus.FORMING);
                pool.setWindowExpiresAt(LocalDateTime.now().plusSeconds(10)); // retry in 10s
                ridePoolRepository.save(pool);
                log.warn("No cabs available for pool {}. Will retry.", poolId);

                sseService.emitToPool(poolId, "POOL_WAITING",
                        "Looking for a driver. Please wait...");
                return;
            }

            // 5. Assign cab to pool
            pool.setCab(assignedCab);
            pool.setStatus(PoolStatus.CONFIRMED);
            pool.setDispatchedAt(LocalDateTime.now());
            ridePoolRepository.save(pool);

            // 6. Calculate final fares for all riders
            for (RideRequest rider : activeRiders) {
                rider.setStatus(RideStatus.CONFIRMED);
                pricingService.calculateAndPersist(rider, totalPassengers);
                rideRequestRepository.save(rider);
            }

            log.info("Pool {} dispatched: cab={}, driver={}, bookings={}, totalPassengers={}",
                    poolId, assignedCab.getId(), assignedCab.getDriverName(),
                    activeRiders.size(), totalPassengers);

            // 7. SSE notifications
            String driverName = assignedCab.getDriverName();
            String licensePlate = assignedCab.getLicensePlate();

            // Notify all passengers
            for (RideRequest rider : activeRiders) {
                sseService.emitToPassenger(rider.getPassenger().getId(), "POOL_DISPATCHED",
                        java.util.Map.of(
                                "poolId", poolId.toString(),
                                "cabId", assignedCab.getId().toString(),
                                "driverName", driverName,
                                "licensePlate", licensePlate,
                                "poolSize", totalPassengers,
                                "fare", rider.getEstimatedPrice() != null ? rider.getEstimatedPrice() : 0));
            }

            // Notify driver
            sseService.emitToDriver(assignedCab.getId(), "TRIP_ASSIGNED",
                    java.util.Map.of(
                            "poolId", poolId.toString(),
                            "riders", activeRiders.size(),
                            "pickupLat", pool.getPickupLat(),
                            "pickupLng", pool.getPickupLng()));
        });
    }
}
