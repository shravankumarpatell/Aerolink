package com.aerolink.ride.dto.response;

import com.aerolink.ride.enums.CabStatus;
import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CabResponseDTO {
    private UUID id;
    private String licensePlate;
    private String driverName;
    private int totalSeats;
    private int luggageCapacity;
    private double currentLat;
    private double currentLng;
    private CabStatus status;

    // Pool capacity info (only set for ASSIGNED cabs with active pools)
    private Integer remainingSeats;
    private Integer remainingLuggage;
    private UUID poolId;
}
