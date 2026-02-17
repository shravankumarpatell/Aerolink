package com.aerolink.ride.dto.response;

import com.aerolink.ride.enums.RideStatus;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideResponseDTO {
    private UUID id;
    private UUID passengerId;
    private String passengerName;
    private double pickupLat;
    private double pickupLng;
    private double dropLat;
    private double dropLng;
    private int passengerCount;
    private int luggageCount;
    private RideStatus status;
    private UUID ridePoolId;
    private Double estimatedPrice;
    private LocalDateTime createdAt;
}
