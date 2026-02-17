package com.aerolink.ride.entity;

import com.aerolink.ride.enums.PoolStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ride_pools")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RidePool {

    private static final int FIXED_CAPACITY_SEATS = 4;
    private static final int FIXED_CAPACITY_LUGGAGE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cab_id")
    private Cab cab;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PoolStatus status;

    @Column(name = "total_occupied_seats", nullable = false)
    private int totalOccupiedSeats;

    @Column(name = "total_luggage", nullable = false)
    private int totalLuggage;

    @Column(name = "total_route_distance_km", nullable = false)
    private double totalRouteDistanceKm;

    @Column(name = "window_expires_at")
    private LocalDateTime windowExpiresAt;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "pickup_lat", nullable = false)
    private double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private double pickupLng;

    @Column(name = "drop_lat", nullable = false)
    private double dropLat;

    @Column(name = "drop_lng", nullable = false)
    private double dropLng;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "ridePool", fetch = FetchType.LAZY)
    @Builder.Default
    private List<RideRequest> rideRequests = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = PoolStatus.FORMING;
        }
    }

    /**
     * Returns remaining seat capacity.
     * Before dispatch (no cab assigned): uses fixed capacity (4).
     * After dispatch (cab assigned): uses cab's actual capacity.
     */
    public int getRemainingSeats() {
        int capacity = (cab != null) ? cab.getTotalSeats() : FIXED_CAPACITY_SEATS;
        return capacity - totalOccupiedSeats;
    }

    /**
     * Returns remaining luggage capacity.
     * Before dispatch (no cab assigned): uses fixed capacity (4).
     * After dispatch (cab assigned): uses cab's actual capacity.
     */
    public int getRemainingLuggage() {
        int capacity = (cab != null) ? cab.getLuggageCapacity() : FIXED_CAPACITY_LUGGAGE;
        return capacity - totalLuggage;
    }

    /**
     * Check if pool window has expired.
     */
    public boolean isWindowExpired() {
        return windowExpiresAt != null && LocalDateTime.now().isAfter(windowExpiresAt);
    }

    /**
     * Check if pool is full (4 seats occupied).
     */
    public boolean isFull() {
        return totalOccupiedSeats >= FIXED_CAPACITY_SEATS;
    }

    /**
     * Check if pool is ready for dispatch (full or window expired).
     */
    public boolean isReadyForDispatch() {
        return status == PoolStatus.FORMING && (isFull() || isWindowExpired());
    }
}
