package com.aerolink.ride.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CabRegistrationDTO {

    @NotBlank(message = "License plate is required")
    @Size(max = 20)
    private String licensePlate;

    @NotBlank(message = "Driver name is required")
    @Size(max = 100)
    private String driverName;

    @Min(value = 1)
    @Max(value = 8)
    private int totalSeats = 4;

    @Min(value = 0)
    @Max(value = 10)
    private int luggageCapacity = 4;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double currentLat;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double currentLng;
}
