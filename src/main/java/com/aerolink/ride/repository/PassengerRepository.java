package com.aerolink.ride.repository;

import com.aerolink.ride.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PassengerRepository extends JpaRepository<Passenger, UUID> {
    Optional<Passenger> findByEmail(String email);
}
