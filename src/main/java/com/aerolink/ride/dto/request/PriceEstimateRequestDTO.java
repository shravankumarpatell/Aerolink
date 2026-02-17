package com.aerolink.ride.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceEstimateRequestDTO {

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double pickupLat;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double pickupLng;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double dropLat;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double dropLng;
}
