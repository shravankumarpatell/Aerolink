package com.aerolink.ride.entity;

import com.aerolink.ride.enums.RideStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ride_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @Column(name = "pickup_lat", nullable = false)
    private double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private double pickupLng;

    @Column(name = "drop_lat", nullable = false)
    private double dropLat;

    @Column(name = "drop_lng", nullable = false)
    private double dropLng;

    @Column(name = "passenger_count", nullable = false)
    private int passengerCount;

    @Column(name = "luggage_count", nullable = false)
    private int luggageCount;

    @Column(name = "max_detour_km", nullable = false)
    private double maxDetourKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RideStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_pool_id")
    private RidePool ridePool;

    @Column(name = "estimated_price")
    private Double estimatedPrice;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = RideStatus.PENDING;
        }
    }
}
