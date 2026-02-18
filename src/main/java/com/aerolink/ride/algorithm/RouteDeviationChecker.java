package com.aerolink.ride.algorithm;

import com.aerolink.ride.entity.RidePool;
import com.aerolink.ride.entity.RideRequest;
import com.aerolink.ride.enums.RideStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Route-aware detour checker for ride pooling.
 *
 * <p>
 * Instead of measuring raw point-to-point distances between stops,
 * this algorithm <b>simulates the actual cab route</b> using nearest-
 * neighbor ordering of pickups then drops, and calculates each rider's
 * real detour as {@code (poolRideDistance - directDistance)}.
 * </p>
 *
 * <h3>Complexity:</h3>
 * <ul>
 * <li>Time: O(n²) where n = total stops (≤ 8 in practice → effectively
 * O(1))</li>
 * <li>Space: O(n)</li>
 * </ul>
 */
@Slf4j
public final class RouteDeviationChecker {

    private RouteDeviationChecker() {
        // Utility class
    }

    // ────────────────────────────────────────────────────────────────────
    // Internal type: a geo-stop carrying its rider index and phase
    // ────────────────────────────────────────────────────────────────────

    /** Whether a stop is a pickup or a drop-off. */
    private enum StopType {
        PICKUP, DROP
    }

    /** A stop on the cab route, tagged with its rider index and type. */
    private static final class RouteStop {
        final int riderIndex;
        final StopType type;
        final double lat;
        final double lng;

        RouteStop(int riderIndex, StopType type, double lat, double lng) {
            this.riderIndex = riderIndex;
            this.type = type;
            this.lat = lat;
            this.lng = lng;
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────

    /**
     * Check if adding the new request to the pool keeps every rider's
     * detour within their stated tolerance.
     *
     * <h4>Algorithm:</h4>
     * <ol>
     * <li>Combine existing + new requests.</li>
     * <li>Build an optimal route: pickups in nearest-neighbor order,
     * then drops in nearest-neighbor order.</li>
     * <li>Walk the ordered route and, for each rider, accumulate
     * {@code poolRideKm} = distance from their pickup position
     * to their drop position <em>along the route</em>.</li>
     * <li>Compare {@code poolRideKm - directKm} against
     * {@code rider.maxDetourKm}. If any rider exceeds, reject.</li>
     * </ol>
     *
     * @param pool             The candidate ride pool (used for logging)
     * @param newRequest       The new ride request to add
     * @param existingRequests The current members of the pool
     * @return true if all riders remain within their detour tolerance
     */
    public static boolean isWithinDetourTolerance(RidePool pool, RideRequest newRequest,
            List<RideRequest> existingRequests) {
        if (existingRequests == null || existingRequests.isEmpty()) {
            return true;
        }

        // Only consider active riders
        List<RideRequest> activeRequests = existingRequests.stream()
                .filter(r -> r.getStatus() != RideStatus.CANCELLED
                        && r.getStatus() != RideStatus.COMPLETED)
                .collect(Collectors.toList());

        if (activeRequests.isEmpty()) {
            return true;
        }

        // ── 1. Combine all riders (existing + new) ──────────────────
        List<RideRequest> allRiders = new ArrayList<>(activeRequests);
        allRiders.add(newRequest);

        // ── 2. Build the optimal route ──────────────────────────────
        List<RouteStop> orderedRoute = buildOptimalRoute(allRiders);

        // ── 3. Pre-compute cumulative distance along the route ──────
        // cumulativeKm[i] = total distance from orderedRoute[0] to orderedRoute[i]
        double[] cumulativeKm = new double[orderedRoute.size()];
        cumulativeKm[0] = 0.0;
        for (int i = 1; i < orderedRoute.size(); i++) {
            RouteStop prev = orderedRoute.get(i - 1);
            RouteStop curr = orderedRoute.get(i);
            cumulativeKm[i] = cumulativeKm[i - 1]
                    + DistanceCalculator.calculateKm(prev.lat, prev.lng, curr.lat, curr.lng);
        }

        // ── 4. For each rider, find pickup & drop positions, compute detour ─
        for (int riderIdx = 0; riderIdx < allRiders.size(); riderIdx++) {
            RideRequest rider = allRiders.get(riderIdx);

            // Find this rider's pickup and drop positions in the ordered route
            int pickupPos = -1;
            int dropPos = -1;
            for (int i = 0; i < orderedRoute.size(); i++) {
                RouteStop stop = orderedRoute.get(i);
                if (stop.riderIndex == riderIdx && stop.type == StopType.PICKUP) {
                    pickupPos = i;
                }
                if (stop.riderIndex == riderIdx && stop.type == StopType.DROP) {
                    dropPos = i;
                }
            }

            if (pickupPos == -1 || dropPos == -1) {
                continue; // safety guard
            }

            // Pool ride = distance along the route from pickup to drop
            double poolRideKm = cumulativeKm[dropPos] - cumulativeKm[pickupPos];

            // Direct ride = straight haversine distance
            double directKm = DistanceCalculator.calculateKm(
                    rider.getPickupLat(), rider.getPickupLng(),
                    rider.getDropLat(), rider.getDropLng());

            double detourKm = Math.max(0, poolRideKm - directKm);

            if (detourKm > rider.getMaxDetourKm()) {
                log.debug("Route-aware detour of {} km exceeds tolerance of {} km for rider {} "
                        + "(poolRide={} km, direct={} km)",
                        String.format("%.2f", detourKm),
                        String.format("%.2f", rider.getMaxDetourKm()),
                        rider.getId(),
                        String.format("%.2f", poolRideKm),
                        String.format("%.2f", directKm));
                return false;
            }
        }

        log.debug("All {} riders within detour tolerance — pooling approved", allRiders.size());
        return true;
    }

    /**
     * Calculate the estimated total route distance for a shared cab serving all
     * riders in the pool, including a new request.
     *
     * <p>
     * Computes the actual cab driving path by sequencing all pickup stops
     * (nearest-neighbor order) then all drop stops (nearest-neighbor order).
     * Identical or co-located stops naturally collapse to ~0 km between them.
     * </p>
     *
     * @param existingRequests Current pool members
     * @param newRequest       New request to add
     * @return Estimated total cab route distance in km
     */
    public static double estimateTotalRouteDistance(List<RideRequest> existingRequests,
            RideRequest newRequest) {

        List<RideRequest> allRequests = new ArrayList<>();
        if (existingRequests != null) {
            existingRequests.stream()
                    .filter(r -> r.getStatus() != RideStatus.CANCELLED
                            && r.getStatus() != RideStatus.COMPLETED)
                    .forEach(allRequests::add);
        }
        allRequests.add(newRequest);

        List<RouteStop> route = buildOptimalRoute(allRequests);

        double total = 0.0;
        for (int i = 1; i < route.size(); i++) {
            RouteStop prev = route.get(i - 1);
            RouteStop curr = route.get(i);
            total += DistanceCalculator.calculateKm(prev.lat, prev.lng, curr.lat, curr.lng);
        }

        return Math.round(total * 100.0) / 100.0;
    }

    // ────────────────────────────────────────────────────────────────────
    // Route building (shared by both public methods)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Build an optimal cab route: visit all pickups in nearest-neighbor
     * order, then all drops in nearest-neighbor order.
     *
     * @param allRiders All ride requests (existing + new)
     * @return Ordered list of stops the cab should visit
     */
    private static List<RouteStop> buildOptimalRoute(List<RideRequest> allRiders) {
        // Build pickup and drop stop lists
        List<RouteStop> pickupStops = new ArrayList<>();
        List<RouteStop> dropStops = new ArrayList<>();
        for (int i = 0; i < allRiders.size(); i++) {
            RideRequest r = allRiders.get(i);
            pickupStops.add(new RouteStop(i, StopType.PICKUP, r.getPickupLat(), r.getPickupLng()));
            dropStops.add(new RouteStop(i, StopType.DROP, r.getDropLat(), r.getDropLng()));
        }

        List<RouteStop> orderedRoute = new ArrayList<>();

        // Phase 1: Order pickups by nearest-neighbor from the first pickup
        List<RouteStop> remaining = new ArrayList<>(pickupStops);
        RouteStop current = remaining.remove(0);
        orderedRoute.add(current);

        while (!remaining.isEmpty()) {
            int nearestIdx = findNearestStop(current, remaining);
            current = remaining.remove(nearestIdx);
            orderedRoute.add(current);
        }

        // Phase 2: Order drops by nearest-neighbor from the last pickup
        remaining = new ArrayList<>(dropStops);
        while (!remaining.isEmpty()) {
            int nearestIdx = findNearestStop(current, remaining);
            current = remaining.remove(nearestIdx);
            orderedRoute.add(current);
        }

        return orderedRoute;
    }

    /**
     * Find the index of the nearest stop in the list to the given stop.
     */
    private static int findNearestStop(RouteStop from, List<RouteStop> stops) {
        int bestIdx = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < stops.size(); i++) {
            double d = DistanceCalculator.calculateKm(
                    from.lat, from.lng, stops.get(i).lat, stops.get(i).lng);
            if (d < bestDist) {
                bestDist = d;
                bestIdx = i;
            }
        }
        return bestIdx;
    }
}
