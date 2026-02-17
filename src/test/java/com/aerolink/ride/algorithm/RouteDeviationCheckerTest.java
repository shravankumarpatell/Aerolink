package com.aerolink.ride.algorithm;

import com.aerolink.ride.entity.Cab;
import com.aerolink.ride.entity.RidePool;
import com.aerolink.ride.entity.RideRequest;
import com.aerolink.ride.enums.CabStatus;
import com.aerolink.ride.enums.PoolStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@DisplayName("RouteDeviationChecker - Detour Tolerance Tests")
class RouteDeviationCheckerTest {

    @Test
    @DisplayName("Should accept when no existing riders in pool")
    void testEmptyPool() {
        RidePool pool = buildPool();
        RideRequest newRequest = buildRequest(19.09, 72.87, 19.10, 72.88, 3.0);

        assertTrue(RouteDeviationChecker.isWithinDetourTolerance(pool, newRequest, List.of()));
        assertTrue(RouteDeviationChecker.isWithinDetourTolerance(pool, newRequest, null));
    }

    @Test
    @DisplayName("Should accept when detour is within tolerance for nearby destinations")
    void testNearbyDestinations() {
        RidePool pool = buildPool();
        RideRequest existing = buildRequest(19.09, 72.87, 19.10, 72.88, 5.0); // 5km tolerance
        RideRequest newRequest = buildRequest(19.091, 72.871, 19.101, 72.881, 3.0); // Very close

        assertTrue(RouteDeviationChecker.isWithinDetourTolerance(pool, newRequest, List.of(existing)));
    }

    @Test
    @DisplayName("Should reject when detour exceeds tolerance for far destinations")
    void testFarDestinations() {
        RidePool pool = buildPool();
        // Existing rider with tight 0.5 km tolerance
        RideRequest existing = buildRequest(19.09, 72.87, 19.10, 72.88, 0.5);
        // New rider going to completely different destination
        RideRequest newRequest = buildRequest(19.09, 72.87, 19.20, 72.98, 3.0);

        assertFalse(RouteDeviationChecker.isWithinDetourTolerance(pool, newRequest, List.of(existing)));
    }

    @Test
    @DisplayName("Should calculate positive route distance estimate")
    void testEstimateTotalRouteDistance() {
        RideRequest request1 = buildRequest(19.09, 72.87, 19.10, 72.88, 3.0);
        RideRequest request2 = buildRequest(19.091, 72.871, 19.11, 72.89, 3.0);

        double distance = RouteDeviationChecker.estimateTotalRouteDistance(List.of(request1), request2);
        assertTrue(distance > 0, "Total route distance should be positive");
    }

    @Test
    @DisplayName("Should handle single new request with no existing riders")
    void testSingleNewRequest() {
        RideRequest newRequest = buildRequest(19.09, 72.87, 19.10, 72.88, 3.0);
        double distance = RouteDeviationChecker.estimateTotalRouteDistance(List.of(), newRequest);
        assertTrue(distance > 0);
    }

    // ---- Helpers ----

    private RideRequest buildRequest(double pLat, double pLng, double dLat, double dLng, double maxDetour) {
        return RideRequest.builder()
                .id(UUID.randomUUID())
                .pickupLat(pLat)
                .pickupLng(pLng)
                .dropLat(dLat)
                .dropLng(dLng)
                .passengerCount(1)
                .luggageCount(0)
                .maxDetourKm(maxDetour)
                .build();
    }

    private RidePool buildPool() {
        Cab cab = Cab.builder()
                .id(UUID.randomUUID())
                .totalSeats(4)
                .luggageCapacity(4)
                .currentLat(19.09)
                .currentLng(72.87)
                .status(CabStatus.ASSIGNED)
                .build();

        return RidePool.builder()
                .id(UUID.randomUUID())
                .cab(cab)
                .status(PoolStatus.FORMING)
                .totalOccupiedSeats(0)
                .totalLuggage(0)
                .rideRequests(new ArrayList<>())
                .build();
    }
}
