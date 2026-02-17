package com.aerolink.ride.entity;

import com.aerolink.ride.enums.CabStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cabs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cab {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "license_plate", nullable = false, unique = true, length = 20)
    private String licensePlate;

    @Column(name = "driver_name", nullable = false, length = 100)
    private String driverName;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "luggage_capacity", nullable = false)
    private int luggageCapacity;

    @Column(name = "current_lat", nullable = false)
    private double currentLat;

    @Column(name = "current_lng", nullable = false)
    private double currentLng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CabStatus status;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = CabStatus.AVAILABLE;
        }
    }
}
