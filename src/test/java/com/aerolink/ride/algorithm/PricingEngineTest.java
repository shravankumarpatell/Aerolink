package com.aerolink.ride.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@DisplayName("PricingEngine - Dynamic Pricing Tests")
class PricingEngineTest {

    // Standard config values
    private static final double BASE_FARE = 15.0;
    private static final double DISCOUNT_PER_CORIDER = 0.10;
    private static final double MIN_SHARING_DISCOUNT = 0.60;
    private static final double DEMAND_THRESHOLD = 0.7;
    private static final double DEMAND_SENSITIVITY = 0.5;
    private static final double PEAK_SURGE = 1.5;
    private static final double OFF_PEAK_SURGE = 1.0;

    @Test
    @DisplayName("Should calculate base price for solo rider, low demand")
    void testBasePriceSoloRider() {
        PricingEngine.PricingResult result = PricingEngine.calculate(
                10.0, BASE_FARE, 5, 20, 1,
                DISCOUNT_PER_CORIDER, MIN_SHARING_DISCOUNT,
                DEMAND_THRESHOLD, DEMAND_SENSITIVITY,
                PEAK_SURGE, OFF_PEAK_SURGE, 7, 10, 17, 20);

        assertTrue(result.finalPrice() > 0, "Price should be positive");
        assertEquals(1.0, result.sharingDiscount(), "Solo rider should have no sharing discount");
        assertEquals(10.0, result.distanceKm());
    }

    @Test
    @DisplayName("Should apply sharing discount for pool of 3")
    void testSharingDiscount() {
        double discount = PricingEngine.calculateSharingDiscount(3, 0.10, 0.60);
        // 1 - (3-1) * 0.10 = 0.80
        assertEquals(0.80, discount, 0.001);
    }

    @Test
    @DisplayName("Should floor sharing discount at minimum")
    void testSharingDiscountFloor() {
        double discount = PricingEngine.calculateSharingDiscount(8, 0.10, 0.60);
        // 1 - (8-1) * 0.10 = 0.30, but min is 0.60
        assertEquals(0.60, discount, 0.001);
    }

    @Test
    @DisplayName("Should return 1.0 sharing discount for solo rider")
    void testSharingDiscountSolo() {
        double discount = PricingEngine.calculateSharingDiscount(1, 0.10, 0.60);
        assertEquals(1.0, discount, 0.001);
    }

    @Test
    @DisplayName("Should increase demand multiplier when demand exceeds supply")
    void testHighDemandMultiplier() {
        // 50 requests, 10 cabs → ratio 5.0 → high surge
        double multiplier = PricingEngine.calculateDemandMultiplier(50, 10, 0.7, 0.5);
        assertTrue(multiplier > 1.0, "Demand multiplier should be > 1.0 with high demand");
    }

    @Test
    @DisplayName("Should floor demand multiplier at 1.0 when demand is low")
    void testLowDemandMultiplier() {
        // 5 requests, 100 cabs → ratio 0.05 → no surge
        double multiplier = PricingEngine.calculateDemandMultiplier(5, 100, 0.7, 0.5);
        assertEquals(1.0, multiplier, 0.001);
    }

    @Test
    @DisplayName("Should handle zero available cabs without error")
    void testZeroCabsDemand() {
        double multiplier = PricingEngine.calculateDemandMultiplier(50, 0, 0.7, 0.5);
        assertTrue(multiplier > 1.0, "Should apply max surge when no cabs");
    }

    @Test
    @DisplayName("Should produce lower price for pooled ride than solo")
    void testPooledCheaperThanSolo() {
        PricingEngine.PricingResult solo = PricingEngine.calculate(
                10.0, BASE_FARE, 10, 20, 1,
                DISCOUNT_PER_CORIDER, MIN_SHARING_DISCOUNT,
                DEMAND_THRESHOLD, DEMAND_SENSITIVITY,
                PEAK_SURGE, OFF_PEAK_SURGE, 7, 10, 17, 20);

        PricingEngine.PricingResult pooled = PricingEngine.calculate(
                10.0, BASE_FARE, 10, 20, 3,
                DISCOUNT_PER_CORIDER, MIN_SHARING_DISCOUNT,
                DEMAND_THRESHOLD, DEMAND_SENSITIVITY,
                PEAK_SURGE, OFF_PEAK_SURGE, 7, 10, 17, 20);

        assertTrue(pooled.finalPrice() < solo.finalPrice(),
                "Pooled price should be lower than solo");
    }

    @Test
    @DisplayName("Should generate readable breakdown string")
    void testBreakdownString() {
        PricingEngine.PricingResult result = PricingEngine.calculate(
                10.0, BASE_FARE, 5, 20, 2,
                DISCOUNT_PER_CORIDER, MIN_SHARING_DISCOUNT,
                DEMAND_THRESHOLD, DEMAND_SENSITIVITY,
                PEAK_SURGE, OFF_PEAK_SURGE, 7, 10, 17, 20);

        String breakdown = result.breakdown();
        assertNotNull(breakdown);
        assertTrue(breakdown.contains("₹"), "Breakdown should contain rupee symbol");
    }
}
