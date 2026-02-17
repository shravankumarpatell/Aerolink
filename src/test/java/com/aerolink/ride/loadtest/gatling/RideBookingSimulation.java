package com.aerolink.ride.loadtest.gatling;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling simulation for AeroLink Ride Booking API.
 * Tests the full ride request flow including pool assignment, cab allocation,
 * and pricing.
 *
 * Target SLA:
 * - 100 requests/second throughput
 * - <300ms P95 latency
 *
 * Run: mvn gatling:test
 * -Dgatling.simulationClass=com.aerolink.ride.loadtest.gatling.RideBookingSimulation
 */
public class RideBookingSimulation extends Simulation {

    private final String baseUrl = System.getProperty("baseUrl", "http://localhost:8080");

    // Seed data passenger IDs from V2 migration
    private static final String[] PASSENGER_IDS = {
            "a1b2c3d4-1111-1111-1111-000000000001",
            "a1b2c3d4-1111-1111-1111-000000000002",
            "a1b2c3d4-1111-1111-1111-000000000003",
            "a1b2c3d4-1111-1111-1111-000000000004",
            "a1b2c3d4-1111-1111-1111-000000000005",
            "a1b2c3d4-1111-1111-1111-000000000006",
            "a1b2c3d4-1111-1111-1111-000000000007",
            "a1b2c3d4-1111-1111-1111-000000000008"
    };

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(baseUrl)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .shareConnections();

    // Feed: random passenger + location + unique idempotency key
    Iterator<Map<String, Object>> rideFeeder = Stream.generate(() -> {
        Map<String, Object> data = new HashMap<>();
        data.put("passengerId", PASSENGER_IDS[(int) (Math.random() * PASSENGER_IDS.length)]);
        data.put("pickupLat", 19.08 + (Math.random() * 0.03));
        data.put("pickupLng", 72.85 + (Math.random() * 0.03));
        data.put("dropLat", 19.00 + (Math.random() * 0.06));
        data.put("dropLng", 72.82 + (Math.random() * 0.06));
        data.put("passengerCount", 1 + (int) (Math.random() * 2));
        data.put("luggageCount", (int) (Math.random() * 3));
        data.put("maxDetourKm", 2.0 + (Math.random() * 4.0));
        data.put("idempotencyKey", UUID.randomUUID().toString());
        return data;
    }).iterator();

    // Scenario 1: Full ride booking flow
    ScenarioBuilder rideBooking = scenario("Ride Booking")
            .feed(rideFeeder)
            .exec(
                    http("POST /api/v1/rides")
                            .post("/api/v1/rides")
                            .body(StringBody("""
                                    {
                                      "passengerId": "#{passengerId}",
                                      "pickupLat": #{pickupLat},
                                      "pickupLng": #{pickupLng},
                                      "dropLat": #{dropLat},
                                      "dropLng": #{dropLng},
                                      "passengerCount": #{passengerCount},
                                      "luggageCount": #{luggageCount},
                                      "maxDetourKm": #{maxDetourKm},
                                      "idempotencyKey": "#{idempotencyKey}"
                                    }
                                    """))
                            .check(status().in(200, 201))
                            .check(jsonPath("$.id").saveAs("rideId")))
            .pause(Duration.ofMillis(100), Duration.ofMillis(500))
            .exec(
                    http("GET /api/v1/rides/{id}")
                            .get("/api/v1/rides/#{rideId}")
                            .check(status().is(200))
                            .check(jsonPath("$.status").exists()));

    // Scenario 2: Price estimation (read-only, for mixed workload)
    ScenarioBuilder priceCheck = scenario("Price Check")
            .feed(rideFeeder)
            .exec(
                    http("POST /api/v1/pricing/estimate")
                            .post("/api/v1/pricing/estimate")
                            .body(StringBody("""
                                    {
                                      "pickupLat": #{pickupLat},
                                      "pickupLng": #{pickupLng},
                                      "dropLat": #{dropLat},
                                      "dropLng": #{dropLng},
                                      "passengerCount": #{passengerCount}
                                    }
                                    """))
                            .check(status().is(200)));

    // Scenario 3: Health check baseline
    ScenarioBuilder healthCheck = scenario("Health Check")
            .exec(
                    http("GET /actuator/health")
                            .get("/actuator/health")
                            .check(status().is(200)));

    {
        setUp(
                // Mixed workload simulating real traffic
                rideBooking.injectOpen(
                        rampUsersPerSec(1).to(30).during(Duration.ofSeconds(10)),
                        constantUsersPerSec(30).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(50).during(Duration.ofSeconds(10))),
                priceCheck.injectOpen(
                        rampUsersPerSec(1).to(70).during(Duration.ofSeconds(10)),
                        constantUsersPerSec(70).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(100).during(Duration.ofSeconds(10))),
                healthCheck.injectOpen(
                        constantUsersPerSec(5).during(Duration.ofSeconds(50))))
                .protocols(httpProtocol)
                .assertions(
                        // Global SLAs
                        global().responseTime().percentile(95.0).lt(300), // P95 < 300ms
                        global().successfulRequests().percent().gt(90.0), // >90% success
                        // Per-scenario SLAs
                        details("POST /api/v1/pricing/estimate")
                                .responseTime().percentile(95.0).lt(200), // Pricing: P95 < 200ms
                        details("POST /api/v1/rides")
                                .responseTime().percentile(95.0).lt(500) // Booking: P95 < 500ms (heavier)
                );
    }
}
