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
@DisplayName("RideGroupingAlgorithm - Pool Matching Tests")
class RideGroupingAlgorithmTest {

    @Test
    @DisplayName("Should return null when no candidates")
    void testNoCandidates() {
        RideRequest request = buildRequest(19.09, 72.87, 19.10, 72.88, 1, 1, 3.0);
        assertNull(RideGroupingAlgorithm.findBestPool(List.of(), request, 4));
        assertNull(RideGroupingAlgorithm.findBestPool(null, request, 4));
    }

    @Test
    @DisplayName("Should select pool with capacity")
    void testSelectPoolWithCapacity() {
        RideRequest request = buildRequest(19.09, 72.87, 19.10, 72.88, 1, 1, 3.0);
        RidePool pool = buildPool(4, 4, 1, 0);

        RidePool result = RideGroupingAlgorithm.findBestPool(List.of(pool), request, 4);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should reject pool without seat capacity")
    void testRejectPoolNoSeats() {
        RideRequest request = buildRequest(19.09, 72.87, 19.10, 72.88, 2, 1, 3.0);
        RidePool pool = buildPool(4, 4, 3, 1); // Only 1 seat left, need 2

        RidePool result = RideGroupingAlgorithm.findBestPool(List.of(pool), request, 4);
        assertNull(result);
    }

    @Test
    @DisplayName("Should reject pool without luggage capacity")
    void testRejectPoolNoLuggage() {
        RideRequest request = buildRequest(19.09, 72.87, 19.10, 72.88, 1, 3, 3.0);
        RidePool pool = buildPool(4, 3, 1, 2); // Only 1 luggage left, need 3

        RidePool result = RideGroupingAlgorithm.findBestPool(List.of(pool), request, 4);
        assertNull(result);
    }

    @Test
    @DisplayName("Should reject pool at max pool size")
    void testRejectPoolAtMaxSize() {
        RideRequest request = buildRequest(19.09, 72.87, 19.10, 72.88, 1, 1, 3.0);
        RidePool pool = buildPool(8, 8, 2, 2);
        // Add 4 mock requests to hit max pool size of 4
        List<RideRequest> mockRequests = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            mockRequests.add(buildRequest(19.09, 72.87, 19.10, 72.88, 1, 0, 3.0));
        }
        pool.setRideRequests(mockRequests);

        RidePool result = RideGroupingAlgorithm.findBestPool(List.of(pool), request, 4);
        assertNull(result);
    }

    @Test
    @DisplayName("Should prefer pool with more existing riders (sharing benefit)")
    void testPreferHigherSharing() {
        RideRequest request = buildRequest(19.09, 72.87, 19.10, 72.88, 1, 0, 3.0);

        RidePool pool1 = buildPool(4, 4, 1, 0);
        pool1.setRideRequests(List.of(
                buildRequest(19.09, 72.87, 19.10, 72.88, 1, 0, 3.0)));

        RidePool pool2 = buildPool(4, 4, 2, 0);
        pool2.setRideRequests(List.of(
                buildRequest(19.09, 72.87, 19.10, 72.88, 1, 0, 3.0),
                buildRequest(19.09, 72.87, 19.10, 72.88, 1, 0, 3.0)));

        RidePool result = RideGroupingAlgorithm.findBestPool(List.of(pool1, pool2), request, 4);
        assertNotNull(result);
        // Pool2 has more riders, should be preferred
        assertEquals(pool2.getId(), result.getId());
    }

    @Test
    @DisplayName("hasCapacity should validate both seats and luggage")
    void testHasCapacity() {
        RidePool pool = buildPool(4, 4, 2, 2);
        pool.setRideRequests(List.of());

        RideRequest fitsRequest = buildRequest(19.09, 72.87, 19.10, 72.88, 2, 2, 3.0);
        assertTrue(RideGroupingAlgorithm.hasCapacity(pool, fitsRequest, 4));

        RideRequest tooManySeats = buildRequest(19.09, 72.87, 19.10, 72.88, 3, 1, 3.0);
        assertFalse(RideGroupingAlgorithm.hasCapacity(pool, tooManySeats, 4));
    }

    // ---- Helpers ----

    private RideRequest buildRequest(double pickupLat, double pickupLng,
            double dropLat, double dropLng,
            int passengers, int luggage, double maxDetour) {
        return RideRequest.builder()
                .id(UUID.randomUUID())
                .pickupLat(pickupLat)
                .pickupLng(pickupLng)
                .dropLat(dropLat)
                .dropLng(dropLng)
                .passengerCount(passengers)
                .luggageCount(luggage)
                .maxDetourKm(maxDetour)
                .build();
    }

    private RidePool buildPool(int totalSeats, int luggageCapacity, int occupiedSeats, int totalLuggage) {
        Cab cab = Cab.builder()
                .id(UUID.randomUUID())
                .totalSeats(totalSeats)
                .luggageCapacity(luggageCapacity)
                .currentLat(19.09)
                .currentLng(72.87)
                .status(CabStatus.ASSIGNED)
                .build();

        return RidePool.builder()
                .id(UUID.randomUUID())
                .cab(cab)
                .status(PoolStatus.FORMING)
                .totalOccupiedSeats(occupiedSeats)
                .totalLuggage(totalLuggage)
                .rideRequests(new ArrayList<>())
                .build();
    }
}
