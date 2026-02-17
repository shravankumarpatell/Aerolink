package com.aerolink.ride.algorithm;

/**
 * Haversine distance calculator for computing great-circle distance
 * between two points on Earth given their latitude and longitude.
 *
 * <p>
 * Time Complexity: O(1) per calculation
 * </p>
 * <p>
 * Space Complexity: O(1)
 * </p>
 */
public final class DistanceCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private DistanceCalculator() {
        // Utility class
    }

    /**
     * Calculate the Haversine distance between two geo-coordinates.
     *
     * @param lat1 Latitude of point 1 (degrees)
     * @param lng1 Longitude of point 1 (degrees)
     * @param lat2 Latitude of point 2 (degrees)
     * @param lng2 Longitude of point 2 (degrees)
     * @return Distance in kilometers
     */
    public static double calculateKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}
