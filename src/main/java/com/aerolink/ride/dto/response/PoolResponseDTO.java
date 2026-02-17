package com.aerolink.ride.dto.response;

import com.aerolink.ride.enums.PoolStatus;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoolResponseDTO {
    private UUID id;
    private UUID cabId;
    private String cabLicensePlate;
    private String driverName;
    private PoolStatus status;
    private int totalOccupiedSeats;
    private int totalLuggage;
    private int remainingSeats;
    private double totalRouteDistanceKm;
    private LocalDateTime windowExpiresAt;
    private List<RideResponseDTO> riders;
    private LocalDateTime createdAt;
}
