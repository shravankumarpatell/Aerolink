package com.aerolink.ride.algorithm;

import com.aerolink.ride.entity.RidePool;
import com.aerolink.ride.entity.RideRequest;
import com.aerolink.ride.enums.RideStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Checks whether adding a new ride request to an existing pool
 * would cause any current member's detour to exceed their tolerance.
 *
 * <p>
 * Time Complexity: O(k) where k = number of existing riders in the pool
 * </p>
 * <p>
 * Space Complexity: O(1)
 * </p>
 */
@Slf4j
public final class RouteDeviationChecker {

    private RouteDeviationChecker() {
        // Utility class
    }

    /**
     * Check if adding the new request to the pool is within detour tolerance
     * for all existing members.
     *
     * <p>
     * The deviation is estimated by calculating the additional distance
     * each existing rider would experience if the new pickup and drop
     * points are added to the route.
     * </p>
     *
     * @param pool             The candidate ride pool
     * @param newRequest       The new ride request to add
     * @param existingRequests The current members of the pool
     * @return true if all existing members remain within their detour tolerance
     */
    public static boolean isWithinDetourTolerance(RidePool pool, RideRequest newRequest,
            List<RideRequest> existingRequests) {
        if (existingRequests == null || existingRequests.isEmpty()) {
            return true;
        }

        // Only consider active riders — cancelled/completed requests should not block
        // new bookings
        List<RideRequest> activeRequests = existingRequests.stream()
                .filter(r -> r.getStatus() != RideStatus.CANCELLED
                        && r.getStatus() != RideStatus.COMPLETED)
                .collect(Collectors.toList());

        if (activeRequests.isEmpty()) {
            return true;
        }

        for (RideRequest existing : activeRequests) {
            // Realistic detour: measure how much extra distance is added at
            // the pickup side and drop side independently.
            // pickup detour = (existing_pickup → new_pickup) + (new_pickup → existing_drop)
            // - original
            // This models a side-trip to the new pickup then continuing to drop.
            double pickupDetour = DistanceCalculator.calculateKm(
                    existing.getPickupLat(), existing.getPickupLng(),
                    newRequest.getPickupLat(), newRequest.getPickupLng());

            double dropDetour = DistanceCalculator.calculateKm(
                    newRequest.getDropLat(), newRequest.getDropLng(),
                    existing.getDropLat(), existing.getDropLng());

            // Total detour is the extra distance added by picking up
            // and dropping off the new rider along the existing route
            double detourKm = pickupDetour + dropDetour;

            if (detourKm > existing.getMaxDetourKm()) {
                log.debug("Detour of {} km exceeds tolerance of {} km for rider {}",
                        String.format("%.2f", detourKm),
                        String.format("%.2f", existing.getMaxDetourKm()),
                        existing.getId());
                return false;
            }
        }

        return true;
    }

    /**
     * Calculate the estimated total route distance if a new request is added to a
     * pool.
     *
     * @param existingRequests Current pool members
     * @param newRequest       New request to add
     * @return Estimated total route distance in km
     */
    public static double estimateTotalRouteDistance(List<RideRequest> existingRequests,
            RideRequest newRequest) {
        double totalDistance = 0.0;

        // Add the new request's own distance
        totalDistance += DistanceCalculator.calculateKm(
                newRequest.getPickupLat(), newRequest.getPickupLng(),
                newRequest.getDropLat(), newRequest.getDropLng());

        if (existingRequests != null) {
            List<RideRequest> activeRequests = existingRequests.stream()
                    .filter(r -> r.getStatus() != RideStatus.CANCELLED
                            && r.getStatus() != RideStatus.COMPLETED)
                    .collect(Collectors.toList());
            for (RideRequest existing : activeRequests) {
                totalDistance += DistanceCalculator.calculateKm(
                        existing.getPickupLat(), existing.getPickupLng(),
                        existing.getDropLat(), existing.getDropLng());
            }

            // Add inter-stop distances (connections between riders)
            if (!activeRequests.isEmpty()) {
                RideRequest last = activeRequests.get(activeRequests.size() - 1);
                totalDistance += DistanceCalculator.calculateKm(
                        last.getDropLat(), last.getDropLng(),
                        newRequest.getPickupLat(), newRequest.getPickupLng());
            }
        }

        return totalDistance;
    }
}
