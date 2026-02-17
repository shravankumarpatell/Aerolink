package com.aerolink.ride.dto.event;

import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideEvent implements Serializable {
    private String eventType;
    private UUID rideRequestId;
    private UUID ridePoolId;
    private UUID passengerId;
    private String reason;
    private LocalDateTime timestamp;

    public static RideEvent cancellation(UUID rideRequestId, UUID ridePoolId, UUID passengerId, String reason) {
        return RideEvent.builder()
                .eventType("RIDE_CANCELLED")
                .rideRequestId(rideRequestId)
                .ridePoolId(ridePoolId)
                .passengerId(passengerId)
                .reason(reason)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static RideEvent pooled(UUID rideRequestId, UUID ridePoolId, UUID passengerId) {
        return RideEvent.builder()
                .eventType("RIDE_POOLED")
                .rideRequestId(rideRequestId)
                .ridePoolId(ridePoolId)
                .passengerId(passengerId)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static RideEvent poolDissolved(UUID ridePoolId) {
        return RideEvent.builder()
                .eventType("POOL_DISSOLVED")
                .ridePoolId(ridePoolId)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static RideEvent priceUpdated(UUID rideRequestId, UUID passengerId) {
        return RideEvent.builder()
                .eventType("PRICE_UPDATED")
                .rideRequestId(rideRequestId)
                .passengerId(passengerId)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
