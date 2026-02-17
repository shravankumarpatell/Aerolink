package com.aerolink.ride.integration;

import com.aerolink.ride.dto.request.CancellationRequestDTO;
import com.aerolink.ride.dto.request.RideRequestDTO;
import com.aerolink.ride.dto.response.PoolResponseDTO;
import com.aerolink.ride.dto.response.RideResponseDTO;
import com.aerolink.ride.enums.PoolStatus;
import com.aerolink.ride.enums.RideStatus;
import com.aerolink.ride.repository.CabRepository;
import com.aerolink.ride.repository.PassengerRepository;
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
@DisplayName("Booking Flow Integration Tests")
class BookingFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PassengerRepository passengerRepository;

    @Autowired
    private CabRepository cabRepository;

    @Test
    @DisplayName("Should create ride request and assign to pool")
    void testFullBookingFlow() {
        // Use seed data passenger
        UUID passengerId = UUID.fromString("a1b2c3d4-1111-1111-1111-000000000001");

        // Verify passenger exists from seed data
        assertTrue(passengerRepository.findById(passengerId).isPresent(),
                "Seed passenger should exist");

        // 1. Request a ride
        RideRequestDTO requestDTO = RideRequestDTO.builder()
                .passengerId(passengerId)
                .pickupLat(19.0900)
                .pickupLng(72.8660)
                .dropLat(19.0200)
                .dropLng(72.8500)
                .passengerCount(1)
                .luggageCount(1)
                .maxDetourKm(3.0)
                .idempotencyKey("test-booking-" + UUID.randomUUID())
                .build();

        ResponseEntity<RideResponseDTO> createResponse = restTemplate.postForEntity(
                "/api/v1/rides", requestDTO, RideResponseDTO.class);

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertNotNull(createResponse.getBody());
        assertNotNull(createResponse.getBody().getId());
        assertEquals(RideStatus.POOLED, createResponse.getBody().getStatus());
        assertNotNull(createResponse.getBody().getRidePoolId());
        assertNotNull(createResponse.getBody().getEstimatedPrice());
        assertTrue(createResponse.getBody().getEstimatedPrice() > 0);

        UUID rideId = createResponse.getBody().getId();
        UUID poolId = createResponse.getBody().getRidePoolId();

        // 2. Get ride details
        ResponseEntity<RideResponseDTO> getResponse = restTemplate.getForEntity(
                "/api/v1/rides/" + rideId, RideResponseDTO.class);

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertEquals(rideId, getResponse.getBody().getId());

        // 3. Get pool details
        ResponseEntity<PoolResponseDTO> poolResponse = restTemplate.getForEntity(
                "/api/v1/rides/pool/" + poolId, PoolResponseDTO.class);

        assertEquals(HttpStatus.OK, poolResponse.getStatusCode());
        assertNotNull(poolResponse.getBody());
        assertTrue(poolResponse.getBody().getTotalOccupiedSeats() > 0);
        assertNotNull(poolResponse.getBody().getCabLicensePlate());
    }

    @Test
    @DisplayName("Should handle idempotent ride requests")
    void testIdempotency() {
        UUID passengerId = UUID.fromString("a1b2c3d4-1111-1111-1111-000000000002");
        String idempotencyKey = "idem-test-" + UUID.randomUUID();

        RideRequestDTO requestDTO = RideRequestDTO.builder()
                .passengerId(passengerId)
                .pickupLat(19.0880)
                .pickupLng(72.8640)
                .dropLat(19.0200)
                .dropLng(72.8500)
                .passengerCount(1)
                .luggageCount(0)
                .maxDetourKm(3.0)
                .idempotencyKey(idempotencyKey)
                .build();

        // First request
        ResponseEntity<RideResponseDTO> first = restTemplate.postForEntity(
                "/api/v1/rides", requestDTO, RideResponseDTO.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());

        // Second request with same idempotency key — should return same ride
        ResponseEntity<RideResponseDTO> second = restTemplate.postForEntity(
                "/api/v1/rides", requestDTO, RideResponseDTO.class);
        assertEquals(HttpStatus.CREATED, second.getStatusCode());
        assertEquals(first.getBody().getId(), second.getBody().getId());
    }

    @Test
    @DisplayName("Should pool multiple riders together")
    void testMultipleRidersPooling() {
        // Create 2 rides with nearby pickup/drop
        RideRequestDTO ride1 = RideRequestDTO.builder()
                .passengerId(UUID.fromString("a1b2c3d4-1111-1111-1111-000000000003"))
                .pickupLat(19.0900)
                .pickupLng(72.8660)
                .dropLat(19.05)
                .dropLng(72.85)
                .passengerCount(1)
                .luggageCount(1)
                .maxDetourKm(5.0)
                .idempotencyKey("pool-test-1-" + UUID.randomUUID())
                .build();

        RideRequestDTO ride2 = RideRequestDTO.builder()
                .passengerId(UUID.fromString("a1b2c3d4-1111-1111-1111-000000000004"))
                .pickupLat(19.0910)
                .pickupLng(72.8670)
                .dropLat(19.051)
                .dropLng(72.851)
                .passengerCount(1)
                .luggageCount(0)
                .maxDetourKm(5.0)
                .idempotencyKey("pool-test-2-" + UUID.randomUUID())
                .build();

        ResponseEntity<RideResponseDTO> resp1 = restTemplate.postForEntity(
                "/api/v1/rides", ride1, RideResponseDTO.class);
        ResponseEntity<RideResponseDTO> resp2 = restTemplate.postForEntity(
                "/api/v1/rides", ride2, RideResponseDTO.class);

        assertEquals(HttpStatus.CREATED, resp1.getStatusCode());
        assertEquals(HttpStatus.CREATED, resp2.getStatusCode());

        // Both should be pooled
        assertEquals(RideStatus.POOLED, resp1.getBody().getStatus());
        assertEquals(RideStatus.POOLED, resp2.getBody().getStatus());
    }
}
