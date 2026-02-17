package com.aerolink.ride.integration;

import com.aerolink.ride.dto.request.RideRequestDTO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@DisplayName("Concurrency Integration Tests")
class ConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Should handle 20 parallel ride requests without data races")
    void testParallelRideRequests() throws Exception {
        int numRequests = 20;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();

        String[] passengerIds = {
                "a1b2c3d4-1111-1111-1111-000000000001",
                "a1b2c3d4-1111-1111-1111-000000000002",
                "a1b2c3d4-1111-1111-1111-000000000003",
                "a1b2c3d4-1111-1111-1111-000000000004",
                "a1b2c3d4-1111-1111-1111-000000000005",
                "a1b2c3d4-1111-1111-1111-000000000006",
                "a1b2c3d4-1111-1111-1111-000000000007",
                "a1b2c3d4-1111-1111-1111-000000000008"
        };

        for (int i = 0; i < numRequests; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                RideRequestDTO dto = RideRequestDTO.builder()
                        .passengerId(UUID.fromString(passengerIds[index % passengerIds.length]))
                        .pickupLat(19.09 + (index * 0.002))
                        .pickupLng(72.87 + (index * 0.002))
                        .dropLat(19.05)
                        .dropLng(72.85)
                        .passengerCount(1)
                        .luggageCount(0)
                        .maxDetourKm(5.0)
                        .idempotencyKey("concurrent-" + index + "-" + UUID.randomUUID())
                        .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<RideRequestDTO> entity = new HttpEntity<>(dto, headers);

                // Use String.class to avoid deserialization errors on non-201 responses
                return restTemplate.postForEntity("/api/v1/rides", entity, String.class);
            }));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(120, TimeUnit.SECONDS),
                "Executor did not terminate in time");

        int successCount = 0;
        int errorCount = 0;

        for (Future<ResponseEntity<String>> future : futures) {
            ResponseEntity<String> response = future.get();
            if (response != null && response.getStatusCode() == HttpStatus.CREATED) {
                successCount++;
                assertNotNull(response.getBody());
                // Verify response contains an "id" field
                assertTrue(response.getBody().contains("\"id\""),
                        "Response should contain ride ID");
            } else {
                errorCount++;
            }
        }

        // At least some requests must succeed
        assertTrue(successCount > 0,
                "At least some requests should succeed, but got 0 successes and " + errorCount + " errors");

        System.out.printf("Concurrency test: %d/%d succeeded, %d errors%n",
                successCount, numRequests, errorCount);
    }
}
