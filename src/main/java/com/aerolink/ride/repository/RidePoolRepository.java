package com.aerolink.ride.repository;

import com.aerolink.ride.entity.RidePool;
import com.aerolink.ride.enums.PoolStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RidePoolRepository extends JpaRepository<RidePool, UUID> {

    List<RidePool> findByStatus(PoolStatus status);

    /**
     * Find FORMING pools near a pickup location using the pool's anchor pickup
     * point.
     * Uses Haversine formula for distance calculation.
     */
    @Query("""
                SELECT DISTINCT p FROM RidePool p
                LEFT JOIN FETCH p.rideRequests r
                WHERE p.status = 'FORMING'
                AND (6371.0 * acos(
                    cos(radians(:lat)) * cos(radians(p.pickupLat))
                    * cos(radians(p.pickupLng) - radians(:lng))
                    + sin(radians(:lat)) * sin(radians(p.pickupLat))
                )) <= :radiusKm
            """)
    List<RidePool> findFormingPoolsNear(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm);

    /**
     * Find pools ready for dispatch: FORMING and either full or window expired.
     */
    @Query("""
                SELECT DISTINCT p FROM RidePool p
                LEFT JOIN FETCH p.rideRequests
                WHERE p.status = 'FORMING'
                AND (p.totalOccupiedSeats >= 4 OR p.windowExpiresAt <= :now)
            """)
    List<RidePool> findPoolsReadyForDispatch(@Param("now") LocalDateTime now);

    /**
     * Find FORMING or DISPATCHING pools (stale cleanup).
     */
    @Query("SELECT p FROM RidePool p WHERE p.status IN ('FORMING', 'DISPATCHING')")
    List<RidePool> findStaleFormingOrDispatchingPools();

    /**
     * Pessimistic lock for dispatch operations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM RidePool p WHERE p.id = :id")
    Optional<RidePool> findByIdWithLock(@Param("id") UUID id);

    /**
     * Find all active pools (FORMING, DISPATCHING, CONFIRMED, IN_PROGRESS).
     */
    @Query("""
                SELECT DISTINCT p FROM RidePool p
                LEFT JOIN FETCH p.cab
                LEFT JOIN FETCH p.rideRequests
                WHERE p.status IN ('FORMING', 'DISPATCHING', 'CONFIRMED', 'IN_PROGRESS')
            """)
    List<RidePool> findActivePools();

    /**
     * Find the active pool for a specific cab.
     * Returns List to safely handle edge cases with stale data.
     */
    @Query("""
                SELECT p FROM RidePool p
                WHERE p.cab.id = :cabId
                AND p.status IN ('CONFIRMED', 'IN_PROGRESS')
                ORDER BY p.createdAt DESC
            """)
    List<RidePool> findActivePoolsByCabId(@Param("cabId") UUID cabId);
}
