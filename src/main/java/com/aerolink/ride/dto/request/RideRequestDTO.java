package com.aerolink.ride.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRequestDTO {

    @NotNull(message = "Passenger ID is required")
    private UUID passengerId;

    @NotNull(message = "Pickup latitude is required")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double pickupLat;

    @NotNull(message = "Pickup longitude is required")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double pickupLng;

    @NotNull(message = "Drop latitude is required")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double dropLat;

    @NotNull(message = "Drop longitude is required")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double dropLng;

    @Min(value = 1, message = "At least 1 passenger required")
    @Max(value = 8, message = "Maximum 8 passengers per request")
    private int passengerCount = 1;

    @Min(value = 0)
    @Max(value = 10)
    private int luggageCount = 0;

    @DecimalMin(value = "0.5", message = "Min detour tolerance is 0.5 km")
    private double maxDetourKm = 3.0;

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 64)
    private String idempotencyKey;
}
