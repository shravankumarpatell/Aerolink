package com.aerolink.ride.controller;

import com.aerolink.ride.dto.request.CabLocationUpdateDTO;
import com.aerolink.ride.dto.response.CabResponseDTO;
import com.aerolink.ride.service.CabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cabs")
@RequiredArgsConstructor
@Tag(name = "Cabs", description = "Cab management and driver operations")
public class CabController {

    private final CabService cabService;

    @GetMapping
    @Operation(summary = "List all cabs", description = "Retrieve all cabs with status and pool info.")
    public ResponseEntity<List<CabResponseDTO>> getAllCabs() {
        return ResponseEntity.ok(cabService.getAllCabs());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get cab details", description = "Retrieve cab details by ID.")
    public ResponseEntity<CabResponseDTO> getCab(@PathVariable UUID id) {
        return ResponseEntity.ok(cabService.getCab(id));
    }

    @PatchMapping("/{id}/location")
    @Operation(summary = "Update cab location", description = "Update a cab's GPS coordinates.")
    public ResponseEntity<CabResponseDTO> updateLocation(
            @PathVariable UUID id,
            @Valid @RequestBody CabLocationUpdateDTO dto) {
        return ResponseEntity.ok(cabService.updateLocation(id, dto));
    }

    @PostMapping("/{id}/start-trip")
    @Operation(summary = "Start trip", description = "Driver starts the trip. Sets pool to IN_PROGRESS, rides to IN_PROGRESS, cab to ON_TRIP.")
    public ResponseEntity<CabResponseDTO> startTrip(@PathVariable UUID id) {
        return ResponseEntity.ok(cabService.startTrip(id));
    }

    @PostMapping("/{id}/complete-ride")
    @Operation(summary = "Complete ride", description = "Complete all rides in cab's pool, mark pool completed, make cab available.")
    public ResponseEntity<CabResponseDTO> completeRide(@PathVariable UUID id) {
        return ResponseEntity.ok(cabService.completeRide(id));
    }
}
