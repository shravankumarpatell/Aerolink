package com.aerolink.ride.service;

import com.aerolink.ride.dto.request.CabLocationUpdateDTO;
import com.aerolink.ride.dto.response.CabResponseDTO;
import com.aerolink.ride.entity.Cab;
import com.aerolink.ride.entity.RidePool;
import com.aerolink.ride.entity.RideRequest;
import com.aerolink.ride.enums.CabStatus;
import com.aerolink.ride.enums.PoolStatus;
import com.aerolink.ride.enums.RideStatus;
import com.aerolink.ride.exception.InvalidOperationException;
import com.aerolink.ride.exception.ResourceNotFoundException;
import com.aerolink.ride.repository.CabRepository;
import com.aerolink.ride.repository.RidePoolRepository;
import com.aerolink.ride.repository.RideRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CabService {

    private final CabRepository cabRepository;
    private final RidePoolRepository ridePoolRepository;
    private final RideRequestRepository rideRequestRepository;
    private final SseService sseService;

    @Transactional
    public CabResponseDTO updateLocation(UUID cabId, CabLocationUpdateDTO dto) {
        Cab cab = cabRepository.findById(cabId)
                .orElseThrow(() -> new ResourceNotFoundException("Cab not found: " + cabId));

        cab.setCurrentLat(dto.getLat());
        cab.setCurrentLng(dto.getLng());
        cab = cabRepository.save(cab);

        return buildCabResponse(cab);
    }

    @Transactional(readOnly = true)
    public CabResponseDTO getCab(UUID cabId) {
        Cab cab = cabRepository.findById(cabId)
                .orElseThrow(() -> new ResourceNotFoundException("Cab not found: " + cabId));
        return buildCabResponse(cab);
    }

    @Transactional(readOnly = true)
    public List<CabResponseDTO> getAllCabs() {
        return cabRepository.findAll().stream()
                .map(this::buildCabResponse)
                .collect(Collectors.toList());
    }

    /**
     * Driver starts the trip. Changes pool → IN_PROGRESS, rides → IN_PROGRESS, cab
     * → ON_TRIP.
     */
    @Transactional
    public CabResponseDTO startTrip(UUID cabId) {
        Cab cab = cabRepository.findByIdWithLock(cabId)
                .orElseThrow(() -> new ResourceNotFoundException("Cab not found: " + cabId));

        if (cab.getStatus() != CabStatus.ASSIGNED) {
            throw new InvalidOperationException(
                    "Cab must be ASSIGNED to start a trip. Current status: " + cab.getStatus());
        }

        // Find the CONFIRMED pool for this cab
        List<RidePool> activePools = ridePoolRepository.findActivePoolsByCabId(cabId);
        if (activePools.isEmpty()) {
            throw new ResourceNotFoundException("No active pool found for cab: " + cabId);
        }
        RidePool pool = activePools.get(0);

        if (pool.getStatus() != PoolStatus.CONFIRMED) {
            throw new InvalidOperationException(
                    "Pool must be CONFIRMED to start. Current status: " + pool.getStatus());
        }

        // Update statuses
        pool.setStatus(PoolStatus.IN_PROGRESS);
        ridePoolRepository.save(pool);

        List<RideRequest> activeRiders = rideRequestRepository.findActiveRequestsByPoolId(pool.getId());
        for (RideRequest rider : activeRiders) {
            rider.setStatus(RideStatus.IN_PROGRESS);
            rideRequestRepository.save(rider);
        }

        cab.setStatus(CabStatus.ON_TRIP);
        cab = cabRepository.save(cab);

        log.info("Trip started for cab {} with pool {} ({} riders)",
                cabId, pool.getId(), activeRiders.size());

        // SSE notifications
        for (RideRequest rider : activeRiders) {
            sseService.emitToPassenger(rider.getPassenger().getId(), "RIDE_STARTED",
                    Map.of("message", "Your driver is on the way!",
                            "driverName", cab.getDriverName(),
                            "licensePlate", cab.getLicensePlate()));
        }

        return buildCabResponse(cab);
    }

    /**
     * Complete all rides for a cab's active pool.
     * Sets pool → COMPLETED, rides → COMPLETED, cab location → last drop, cab →
     * AVAILABLE.
     */
    @Transactional
    public CabResponseDTO completeRide(UUID cabId) {
        Cab cab = cabRepository.findByIdWithLock(cabId)
                .orElseThrow(() -> new ResourceNotFoundException("Cab not found: " + cabId));

        if (cab.getStatus() != CabStatus.ON_TRIP && cab.getStatus() != CabStatus.ASSIGNED) {
            throw new InvalidOperationException(
                    "Cab must be ON_TRIP or ASSIGNED to complete. Current status: " + cab.getStatus());
        }

        List<RidePool> activePools = ridePoolRepository.findActivePoolsByCabId(cabId);
        if (activePools.isEmpty()) {
            throw new ResourceNotFoundException("No active pool found for cab: " + cabId);
        }
        RidePool pool = activePools.get(0);

        // Complete all active rides
        List<RideRequest> activeRequests = rideRequestRepository.findActiveRequestsByPoolId(pool.getId());
        double lastDropLat = cab.getCurrentLat();
        double lastDropLng = cab.getCurrentLng();

        for (RideRequest request : activeRequests) {
            request.setStatus(RideStatus.COMPLETED);
            rideRequestRepository.save(request);
            lastDropLat = request.getDropLat();
            lastDropLng = request.getDropLng();

            // SSE notification to each rider
            sseService.emitToPassenger(request.getPassenger().getId(), "RIDE_COMPLETED",
                    Map.of("message", "Your ride is complete. Thank you!",
                            "fare", request.getEstimatedPrice() != null ? request.getEstimatedPrice() : 0));
        }

        // Complete the pool
        pool.setStatus(PoolStatus.COMPLETED);
        ridePoolRepository.save(pool);

        // Release cab
        cab.setCurrentLat(lastDropLat);
        cab.setCurrentLng(lastDropLng);
        cab.setStatus(CabStatus.AVAILABLE);
        Cab savedCab = cabRepository.save(cab);

        // Cleanup SSE tracking
        sseService.cleanupPool(pool.getId());

        log.info("Completed ride for cab {}: {} rides completed", cabId, activeRequests.size());

        return buildCabResponse(savedCab);
    }

    /**
     * Build a full cab response with pool info.
     */
    private CabResponseDTO buildCabResponse(Cab cab) {
        CabResponseDTO dto = CabResponseDTO.builder()
                .id(cab.getId())
                .licensePlate(cab.getLicensePlate())
                .driverName(cab.getDriverName())
                .totalSeats(cab.getTotalSeats())
                .luggageCapacity(cab.getLuggageCapacity())
                .currentLat(cab.getCurrentLat())
                .currentLng(cab.getCurrentLng())
                .status(cab.getStatus())
                .build();

        if (cab.getStatus() == CabStatus.ASSIGNED || cab.getStatus() == CabStatus.ON_TRIP) {
            List<RidePool> activePools = ridePoolRepository.findActivePoolsByCabId(cab.getId());
            if (!activePools.isEmpty()) {
                RidePool pool = activePools.get(0);
                dto.setRemainingSeats(pool.getRemainingSeats());
                dto.setRemainingLuggage(pool.getRemainingLuggage());
                dto.setPoolId(pool.getId());
            }
        } else {
            dto.setRemainingSeats(cab.getTotalSeats());
            dto.setRemainingLuggage(cab.getLuggageCapacity());
        }
        return dto;
    }
}
