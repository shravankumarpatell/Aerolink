package com.aerolink.ride.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CabLocationUpdateDTO {

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double lat;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double lng;
}
