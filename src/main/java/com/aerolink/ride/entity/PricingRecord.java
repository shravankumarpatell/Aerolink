package com.aerolink.ride.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pricing_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_request_id", nullable = false, unique = true)
    private RideRequest rideRequest;

    @Column(name = "base_price", nullable = false)
    private double basePrice;

    @Column(name = "distance_km", nullable = false)
    private double distanceKm;

    @Column(name = "demand_multiplier", nullable = false)
    private double demandMultiplier;

    @Column(name = "sharing_discount", nullable = false)
    private double sharingDiscount;

    @Column(name = "surge_factor", nullable = false)
    private double surgeFactor;

    @Column(name = "final_price", nullable = false)
    private double finalPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
