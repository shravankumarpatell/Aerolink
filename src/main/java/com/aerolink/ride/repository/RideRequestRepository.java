package com.aerolink.ride.repository;

import com.aerolink.ride.entity.RideRequest;
import com.aerolink.ride.enums.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RideRequestRepository extends JpaRepository<RideRequest, UUID> {

    Optional<RideRequest> findByIdempotencyKey(String idempotencyKey);

    List<RideRequest> findByRidePoolId(UUID ridePoolId);

    List<RideRequest> findByPassengerIdAndStatusIn(UUID passengerId, List<RideStatus> statuses);

    /**
     * Count active requests in a time window for demand calculation.
     */
    @Query("""
                SELECT COUNT(r) FROM RideRequest r
                WHERE r.status IN :statuses
                AND r.createdAt >= :since
            """)
    long countActiveRequestsSince(
            @Param("statuses") List<RideStatus> statuses,
            @Param("since") LocalDateTime since);

    /**
     * Find ride requests belonging to a pool that are not cancelled.
     */
    @Query("""
                SELECT r FROM RideRequest r
                WHERE r.ridePool.id = :poolId
                AND r.status <> 'CANCELLED'
            """)
    List<RideRequest> findActiveRequestsByPoolId(@Param("poolId") UUID poolId);

    /**
     * Check if a passenger has any active ride (one-ride-per-user enforcement).
     */
    @Query("""
                SELECT COUNT(r) > 0 FROM RideRequest r
                WHERE r.passenger.id = :passengerId
                AND r.status IN ('PENDING', 'POOLED', 'CONFIRMED', 'IN_PROGRESS')
            """)
    boolean hasActiveRide(@Param("passengerId") UUID passengerId);

    /**
     * Find all rides for a passenger, ordered by creation time (newest first).
     */
    @Query("""
                SELECT r FROM RideRequest r
                LEFT JOIN FETCH r.ridePool
                WHERE r.passenger.id = :passengerId
                ORDER BY r.createdAt DESC
            """)
    List<RideRequest> findByPassengerIdOrderByCreatedAtDesc(@Param("passengerId") UUID passengerId);

    /**
     * Find all active rides for a passenger (for dashboard).
     * Returns as List to safely handle edge cases with multiple active rides.
     */
    @Query("""
                SELECT r FROM RideRequest r
                LEFT JOIN FETCH r.ridePool p
                LEFT JOIN FETCH p.cab
                WHERE r.passenger.id = :passengerId
                AND r.status IN ('PENDING', 'POOLED', 'CONFIRMED', 'IN_PROGRESS')
                ORDER BY r.createdAt DESC
            """)
    List<RideRequest> findActiveRidesByPassengerId(@Param("passengerId") UUID passengerId);
}
