package com.aerolink.ride.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@DisplayName("DistanceCalculator - Haversine Distance Tests")
class DistanceCalculatorTest {

    @Test
    @DisplayName("Should calculate distance between Mumbai Airport and CST (≈24 km)")
    void testMumbaiAirportToCst() {
        // Mumbai Airport: 19.0896, 72.8656
        // CST Station: 18.9398, 72.8355
        double distance = DistanceCalculator.calculateKm(19.0896, 72.8656, 18.9398, 72.8355);
        assertTrue(distance > 15 && distance < 25,
                "Expected ~17km, got: " + distance);
    }

    @Test
    @DisplayName("Should return 0 for same point")
    void testSamePoint() {
        double distance = DistanceCalculator.calculateKm(19.0896, 72.8656, 19.0896, 72.8656);
        assertEquals(0.0, distance, 0.001);
    }

    @Test
    @DisplayName("Should calculate short distance (<1 km)")
    void testShortDistance() {
        // Two points ~0.5 km apart
        double distance = DistanceCalculator.calculateKm(19.0896, 72.8656, 19.0930, 72.8680);
        assertTrue(distance > 0.1 && distance < 1.0,
                "Expected short distance, got: " + distance);
    }

    @Test
    @DisplayName("Should calculate long distance (Mumbai to Delhi ≈1150 km)")
    void testLongDistance() {
        // Mumbai: 19.0760, 72.8777
        // Delhi: 28.6139, 77.2090
        double distance = DistanceCalculator.calculateKm(19.0760, 72.8777, 28.6139, 77.2090);
        assertTrue(distance > 1100 && distance < 1200,
                "Expected ~1150km, got: " + distance);
    }

    @Test
    @DisplayName("Should handle negative coordinates")
    void testNegativeCoordinates() {
        // South America to Africa
        double distance = DistanceCalculator.calculateKm(-23.5505, -46.6333, -33.9249, 18.4241);
        assertTrue(distance > 0, "Distance should be positive");
    }

    @Test
    @DisplayName("Should handle equator crossing")
    void testEquatorCrossing() {
        double distance = DistanceCalculator.calculateKm(-1.0, 36.0, 1.0, 36.0);
        assertTrue(distance > 200 && distance < 250,
                "Expected ~222km, got: " + distance);
    }

    @Test
    @DisplayName("Should be symmetric (A→B == B→A)")
    void testSymmetry() {
        double ab = DistanceCalculator.calculateKm(19.0896, 72.8656, 18.9398, 72.8355);
        double ba = DistanceCalculator.calculateKm(18.9398, 72.8355, 19.0896, 72.8656);
        assertEquals(ab, ba, 0.001);
    }
}
