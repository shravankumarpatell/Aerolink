package com.aerolink.ride.dto.response;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceEstimateDTO {
    private double distanceKm;
    private double baseFarePerKm;
    private double demandMultiplier;
    private double surgeFactor;
    private List<PriceOption> prices;
    private String notes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PriceOption {
        private int poolSize;
        private String label;
        private double sharingDiscount;
        private double price;
    }
}
