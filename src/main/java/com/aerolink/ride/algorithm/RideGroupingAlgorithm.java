package com.aerolink.ride.algorithm;

import com.aerolink.ride.entity.RidePool;
import com.aerolink.ride.entity.RideRequest;
import com.aerolink.ride.enums.RideStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

/**
 * Greedy spatial clustering algorithm for grouping ride requests into pools.
 *
 * <h3>Algorithm:</h3>
 * <ol>
 * <li>Filter candidate pools by seat and luggage capacity — O(m)</li>
 * <li>Check detour tolerance for each candidate — O(m × k)</li>
 * <li>Score remaining candidates by route overlap and sharing benefit —
 * O(m)</li>
 * <li>Select the best-scoring pool — O(m)</li>
 * </ol>
 *
 * <p>
 * Total per-request complexity: O(m × k) where m = candidate pools, k = riders
 * per pool
 * </p>
 * <p>
 * In practice: m ≤ 10, k ≤ 4, so effectively O(1) per request
 * </p>
 */
@Slf4j
public final class RideGroupingAlgorithm {

    private RideGroupingAlgorithm() {
        // Utility class
    }

    /**
     * Find the best pool for a ride request from a list of candidates.
     *
     * @param candidates  List of forming pools near the pickup location
     * @param newRequest  The ride request to place
     * @param maxPoolSize Maximum number of ride requests per pool
     * @return The best-matching pool, or null if no suitable pool exists
     */
    public static RidePool findBestPool(List<RidePool> candidates, RideRequest newRequest,
            int maxPoolSize) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        return candidates.stream()
                .filter(pool -> hasCapacity(pool, newRequest, maxPoolSize))
                .filter(pool -> RouteDeviationChecker.isWithinDetourTolerance(
                        pool, newRequest, pool.getRideRequests()))
                .min(Comparator.comparingDouble(pool -> scorePool(pool, newRequest)))
                .orElse(null);
    }

    /**
     * Check if the pool has enough seats and luggage space for the new request.
     *
     * @return true if the pool can accommodate the new request
     */
    public static boolean hasCapacity(RidePool pool, RideRequest request, int maxPoolSize) {
        if (pool.getRideRequests() != null && pool.getCab() != null) {
            long activeRiders = pool.getRideRequests().stream()
                    .filter(r -> r.getStatus() != RideStatus.CANCELLED
                            && r.getStatus() != RideStatus.COMPLETED)
                    .count();
            // Use the cab's actual seat capacity as the rider limit
            int effectivePoolSize = pool.getCab().getTotalSeats();
            if (activeRiders >= effectivePoolSize) {
                return false;
            }
        }
        if (pool.getRemainingSeats() < request.getPassengerCount()) {
            return false;
        }
        if (pool.getRemainingLuggage() < request.getLuggageCount()) {
            return false;
        }
        return true;
    }

    /**
     * Score a pool for a given request. Lower score = better match.
     *
     * <p>
     * Scoring factors:
     * </p>
     * <ul>
     * <li>Pickup proximity: distance from cab to new pickup</li>
     * <li>Route overlap: how much the new request's route aligns with existing
     * routes</li>
     * <li>Pool utilization: prefer pools with more riders (maximize sharing)</li>
     * </ul>
     *
     * @return A score where lower is better
     */
    static double scorePool(RidePool pool, RideRequest newRequest) {
        double pickupDistance = 0.0;
        if (pool.getCab() != null) {
            pickupDistance = DistanceCalculator.calculateKm(
                    pool.getCab().getCurrentLat(), pool.getCab().getCurrentLng(),
                    newRequest.getPickupLat(), newRequest.getPickupLng());
        }

        // Calculate average drop distance to existing riders' drops
        double avgDropProximity = 0.0;
        if (pool.getRideRequests() != null && !pool.getRideRequests().isEmpty()) {
            avgDropProximity = pool.getRideRequests().stream()
                    .mapToDouble(r -> DistanceCalculator.calculateKm(
                            r.getDropLat(), r.getDropLng(),
                            newRequest.getDropLat(), newRequest.getDropLng()))
                    .average()
                    .orElse(0.0);
        }

        // Sharing benefit: more riders = better (negative weight)
        int currentRiders = pool.getRideRequests() != null ? pool.getRideRequests().size() : 0;
        double sharingBonus = -currentRiders * 2.0;

        return pickupDistance * 0.4 + avgDropProximity * 0.4 + sharingBonus;
    }
}
