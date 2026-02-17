package com.aerolink.ride.controller;

import com.aerolink.ride.dto.request.CancellationRequestDTO;
import com.aerolink.ride.dto.request.RideRequestDTO;
import com.aerolink.ride.dto.response.PoolResponseDTO;
import com.aerolink.ride.dto.response.RideResponseDTO;
import com.aerolink.ride.service.CancellationService;
import com.aerolink.ride.service.RidePoolingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
@Tag(name = "Rides", description = "Ride request and pooling management")
public class RideController {

    private final RidePoolingService ridePoolingService;
    private final CancellationService cancellationService;

    @GetMapping
    @Operation(summary = "List all rides", description = "Retrieve all ride requests.")
    public ResponseEntity<List<RideResponseDTO>> getAllRides() {
        return ResponseEntity.ok(ridePoolingService.getAllRides());
    }

    @PostMapping
    @Operation(summary = "Request a ride", description = "Submit a ride request. Joins or creates a pool with 60s window. No cab assigned until dispatch.")
    public ResponseEntity<RideResponseDTO> requestRide(@Valid @RequestBody RideRequestDTO dto) {
        RideResponseDTO response = ridePoolingService.requestRide(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ride details")
    public ResponseEntity<RideResponseDTO> getRide(@PathVariable UUID id) {
        return ResponseEntity.ok(ridePoolingService.getRide(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a ride", description = "Cancel a ride. Recalculates pool fares if dispatched.")
    public ResponseEntity<Void> cancelRide(@PathVariable UUID id,
            @Valid @RequestBody(required = false) CancellationRequestDTO dto) {
        String reason = dto != null ? dto.getReason() : "No reason provided";
        cancellationService.cancelRide(id, reason);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pool/{poolId}")
    @Operation(summary = "Get pool details", description = "Pool details with riders and window expiration.")
    public ResponseEntity<PoolResponseDTO> getPool(@PathVariable UUID poolId) {
        return ResponseEntity.ok(ridePoolingService.getPool(poolId));
    }

    @GetMapping("/passenger/{passengerId}/dashboard")
    @Operation(summary = "Passenger dashboard", description = "Active ride, pool info, and ride history.")
    public ResponseEntity<RidePoolingService.PassengerDashboardData> getPassengerDashboard(
            @PathVariable UUID passengerId) {
        return ResponseEntity.ok(ridePoolingService.getPassengerDashboard(passengerId));
    }

    @GetMapping("/driver/{cabId}/dashboard")
    @Operation(summary = "Driver dashboard", description = "Active pool and rider details.")
    public ResponseEntity<RidePoolingService.DriverDashboardData> getDriverDashboard(@PathVariable UUID cabId) {
        return ResponseEntity.ok(ridePoolingService.getDriverDashboard(cabId));
    }
}
