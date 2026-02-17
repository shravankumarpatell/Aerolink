package com.aerolink.ride.algorithm;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * Dynamic pricing engine that calculates ride fares based on multiple factors.
 *
 * <h3>Formula:</h3>
 * 
 * <pre>
 * finalPrice = baseFarePerKm × distanceKm × demandMultiplier × sharingDiscount × surgeFactor
 * </pre>
 *
 * <p>
 * Time Complexity: O(1)
 * </p>
 * <p>
 * Space Complexity: O(1)
 * </p>
 */
@Slf4j
public final class PricingEngine {

    private PricingEngine() {
        // Utility class
    }

    /**
     * Calculate the final ride price.
     *
     * @param distanceKm         Trip distance in kilometers
     * @param baseFarePerKm      Base fare per km (e.g., ₹15)
     * @param activeRequests     Number of currently active ride requests
     * @param availableCabs      Number of currently available cabs
     * @param poolSize           Number of riders sharing the pool (1 = solo)
     * @param discountPerCoRider Discount per additional co-rider (e.g., 0.10 = 10%)
     * @param minSharingDiscount Minimum sharing discount (floor, e.g., 0.60)
     * @param demandThreshold    Demand threshold for surge pricing (e.g., 0.7)
     * @param demandSensitivity  Sensitivity of demand multiplier (e.g., 0.5)
     * @param peakSurgeFactor    Surge factor during peak hours (e.g., 1.5)
     * @param offPeakSurgeFactor Surge factor during off-peak (e.g., 1.0)
     * @param peakHoursStart     Start of morning peak (hour, e.g., 7)
     * @param peakHoursEnd       End of morning peak (hour, e.g., 10)
     * @param eveningPeakStart   Start of evening peak (hour, e.g., 17)
     * @param eveningPeakEnd     End of evening peak (hour, e.g., 20)
     * @return Calculated pricing result
     */
    public static PricingResult calculate(
            double distanceKm,
            double baseFarePerKm,
            long activeRequests,
            long availableCabs,
            int poolSize,
            double discountPerCoRider,
            double minSharingDiscount,
            double demandThreshold,
            double demandSensitivity,
            double peakSurgeFactor,
            double offPeakSurgeFactor,
            int peakHoursStart,
            int peakHoursEnd,
            int eveningPeakStart,
            int eveningPeakEnd) {
        // 1. Demand multiplier
        double demandMultiplier = calculateDemandMultiplier(
                activeRequests, availableCabs, demandThreshold, demandSensitivity);

        // 2. Sharing discount
        double sharingDiscount = calculateSharingDiscount(
                poolSize, discountPerCoRider, minSharingDiscount);

        // 3. Surge factor (time-based)
        double surgeFactor = calculateSurgeFactor(
                peakSurgeFactor, offPeakSurgeFactor,
                peakHoursStart, peakHoursEnd,
                eveningPeakStart, eveningPeakEnd);

        // 4. Final price
        double finalPrice = baseFarePerKm * distanceKm * demandMultiplier * sharingDiscount * surgeFactor;
        finalPrice = Math.round(finalPrice * 100.0) / 100.0; // Round to 2 decimal places

        log.debug("Pricing: dist={:.2f}km, base={:.2f}, demand={:.2f}, sharing={:.2f}, surge={:.2f} → ₹{:.2f}",
                distanceKm, baseFarePerKm, demandMultiplier, sharingDiscount, surgeFactor, finalPrice);

        return new PricingResult(baseFarePerKm, distanceKm, demandMultiplier, sharingDiscount, surgeFactor, finalPrice);
    }

    /**
     * Demand multiplier: increases price when demand exceeds supply.
     * Formula: max(1.0, 1 + (demand_ratio - threshold) × sensitivity)
     */
    static double calculateDemandMultiplier(long activeRequests, long availableCabs,
            double threshold, double sensitivity) {
        if (availableCabs <= 0) {
            return 1.0 + sensitivity; // Max surge when no cabs
        }
        double demandRatio = (double) activeRequests / availableCabs;
        return Math.max(1.0, 1.0 + (demandRatio - threshold) * sensitivity);
    }

    /**
     * Sharing discount: reduces price per person when sharing.
     * Formula: 1 - (poolSize - 1) × discountPerCoRider, floored at minDiscount
     */
    static double calculateSharingDiscount(int poolSize, double discountPerCoRider,
            double minDiscount) {
        if (poolSize <= 1)
            return 1.0;
        double discount = 1.0 - (poolSize - 1) * discountPerCoRider;
        return Math.max(minDiscount, discount);
    }

    /**
     * Surge factor based on time of day.
     */
    static double calculateSurgeFactor(double peakFactor, double offPeakFactor,
            int peakStart, int peakEnd,
            int eveningStart, int eveningEnd) {
        int hour = LocalDateTime.now().getHour();
        if ((hour >= peakStart && hour < peakEnd) || (hour >= eveningStart && hour < eveningEnd)) {
            return peakFactor;
        }
        return offPeakFactor;
    }

    /**
     * Immutable result of a pricing calculation.
     */
    public record PricingResult(
            double basePrice,
            double distanceKm,
            double demandMultiplier,
            double sharingDiscount,
            double surgeFactor,
            double finalPrice) {
        public String breakdown() {
            return String.format(
                    "₹%.2f/km × %.2fkm × %.2f(demand) × %.2f(sharing) × %.2f(surge) = ₹%.2f",
                    basePrice, distanceKm, demandMultiplier, sharingDiscount, surgeFactor, finalPrice);
        }
    }
}
