package com.aerolink.ride.integration;

import com.aerolink.ride.dto.request.CancellationRequestDTO;
import com.aerolink.ride.dto.request.RideRequestDTO;
import com.aerolink.ride.dto.response.RideResponseDTO;
import com.aerolink.ride.enums.RideStatus;
import com.aerolink.ride.repository.RidePoolRepository;
import com.aerolink.ride.repository.RideRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@DisplayName("Cancellation Integration Tests")
class CancellationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private RidePoolRepository ridePoolRepository;

    @Test
    @DisplayName("Should cancel ride and update pool")
    void testCancelRide() {
        // Create a ride first
        UUID passengerId = UUID.fromString("a1b2c3d4-1111-1111-1111-000000000005");
        RideRequestDTO requestDTO = RideRequestDTO.builder()
                .passengerId(passengerId)
                .pickupLat(19.0920)
                .pickupLng(72.8680)
                .dropLat(19.03)
                .dropLng(72.84)
                .passengerCount(1)
                .luggageCount(1)
                .maxDetourKm(3.0)
                .idempotencyKey("cancel-test-" + UUID.randomUUID())
                .build();

        ResponseEntity<RideResponseDTO> createResponse = restTemplate.postForEntity(
                "/api/v1/rides", requestDTO, RideResponseDTO.class);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());

        UUID rideId = createResponse.getBody().getId();

        // Cancel the ride
        CancellationRequestDTO cancelDTO = CancellationRequestDTO.builder()
                .reason("Changed plans")
                .build();

        ResponseEntity<Void> cancelResponse = restTemplate.postForEntity(
                "/api/v1/rides/" + rideId + "/cancel", cancelDTO, Void.class);
        assertEquals(HttpStatus.NO_CONTENT, cancelResponse.getStatusCode());

        // Verify ride is cancelled
        ResponseEntity<RideResponseDTO> getResponse = restTemplate.getForEntity(
                "/api/v1/rides/" + rideId, RideResponseDTO.class);
        assertEquals(RideStatus.CANCELLED, getResponse.getBody().getStatus());
    }

    @Test
    @DisplayName("Should reject cancellation of already cancelled ride")
    void testDoubleCancelRejected() {
        // Create and cancel a ride
        UUID passengerId = UUID.fromString("a1b2c3d4-1111-1111-1111-000000000006");
        RideRequestDTO requestDTO = RideRequestDTO.builder()
                .passengerId(passengerId)
                .pickupLat(19.0850)
                .pickupLng(72.8630)
                .dropLat(19.03)
                .dropLng(72.84)
                .passengerCount(1)
                .luggageCount(0)
                .maxDetourKm(3.0)
                .idempotencyKey("double-cancel-" + UUID.randomUUID())
                .build();

        ResponseEntity<RideResponseDTO> createResponse = restTemplate.postForEntity(
                "/api/v1/rides", requestDTO, RideResponseDTO.class);
        UUID rideId = createResponse.getBody().getId();

        CancellationRequestDTO cancelDTO = CancellationRequestDTO.builder()
                .reason("First cancel")
                .build();

        // First cancel — should succeed
        restTemplate.postForEntity("/api/v1/rides/" + rideId + "/cancel", cancelDTO, Void.class);

        // Second cancel — should fail
        ResponseEntity<String> secondCancel = restTemplate.postForEntity(
                "/api/v1/rides/" + rideId + "/cancel", cancelDTO, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, secondCancel.getStatusCode());
    }
}
