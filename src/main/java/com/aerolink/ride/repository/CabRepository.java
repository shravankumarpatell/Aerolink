package com.aerolink.ride.repository;

import com.aerolink.ride.entity.Cab;
import com.aerolink.ride.enums.CabStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CabRepository extends JpaRepository<Cab, UUID> {

    /**
     * Find available cabs near a location using Haversine distance in SQL.
     * Orders by distance ascending. Uses the idx_cabs_status index.
     */
    @Query("""
                SELECT c FROM Cab c
                WHERE c.status = :status
                AND (6371.0 * acos(
                    cos(radians(:lat)) * cos(radians(c.currentLat))
                    * cos(radians(c.currentLng) - radians(:lng))
                    + sin(radians(:lat)) * sin(radians(c.currentLat))
                )) <= :radiusKm
                ORDER BY (6371.0 * acos(
                    cos(radians(:lat)) * cos(radians(c.currentLat))
                    * cos(radians(c.currentLng) - radians(:lng))
                    + sin(radians(:lat)) * sin(radians(c.currentLat))
                )) ASC
            """)
    List<Cab> findAvailableCabsNear(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm,
            @Param("status") CabStatus status);

    /**
     * Lock a cab row for assignment (pessimistic write lock).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cab c WHERE c.id = :id")
    Optional<Cab> findByIdWithLock(@Param("id") UUID id);

    List<Cab> findByStatus(CabStatus status);

    Optional<Cab> findByLicensePlate(String licensePlate);

    /**
     * Find ASSIGNED cabs near a location whose active pool still has
     * remaining seat and luggage capacity for the requesting passenger.
     * Only considers pools in FORMING or CONFIRMED status.
     */
    @Query("""
                SELECT c FROM Cab c
                JOIN RidePool p ON p.cab = c
                WHERE c.status = 'ASSIGNED'
                AND p.status IN ('FORMING', 'CONFIRMED')
                AND (c.totalSeats - p.totalOccupiedSeats) >= :passengerCount
                AND (c.luggageCapacity - p.totalLuggage) >= :luggageCount
                AND (6371.0 * acos(
                    cos(radians(:lat)) * cos(radians(c.currentLat))
                    * cos(radians(c.currentLng) - radians(:lng))
                    + sin(radians(:lat)) * sin(radians(c.currentLat))
                )) <= :radiusKm
                ORDER BY (6371.0 * acos(
                    cos(radians(:lat)) * cos(radians(c.currentLat))
                    * cos(radians(c.currentLng) - radians(:lng))
                    + sin(radians(:lat)) * sin(radians(c.currentLat))
                )) ASC
            """)
    List<Cab> findAssignedCabsWithCapacityNear(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm,
            @Param("passengerCount") int passengerCount,
            @Param("luggageCount") int luggageCount);
}
