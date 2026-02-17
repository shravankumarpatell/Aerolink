package com.aerolink.ride.loadtest.gatling;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling simulation for AeroLink Price Estimation API.
 *
 * Target SLA:
 * - 100 requests/second throughput
 * - <300ms P95 latency
 *
 * Run: mvn gatling:test
 * -Dgatling.simulationClass=com.aerolink.ride.loadtest.gatling.PriceEstimationSimulation
 */
public class PriceEstimationSimulation extends Simulation {

    // Base URL - configurable via system property
    private final String baseUrl = System.getProperty("baseUrl", "http://localhost:8080");

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(baseUrl)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .shareConnections();

    // Feed random pickup/drop locations near Mumbai airport
    Iterator<Map<String, Object>> locationFeeder = Stream.generate(() -> {
        Map<String, Object> data = new HashMap<>();
        double pickupLat = 19.08 + (Math.random() * 0.03); // 19.08 - 19.11
        double pickupLng = 72.85 + (Math.random() * 0.03); // 72.85 - 72.88
        double dropLat = 19.00 + (Math.random() * 0.06); // 19.00 - 19.06
        double dropLng = 72.82 + (Math.random() * 0.06); // 72.82 - 72.88
        int passengers = 1 + (int) (Math.random() * 3); // 1-3 passengers
        data.put("pickupLat", pickupLat);
        data.put("pickupLng", pickupLng);
        data.put("dropLat", dropLat);
        data.put("dropLng", dropLng);
        data.put("passengerCount", passengers);
        return data;
    }).iterator();

    // Scenario: Price estimation (read-only, lightweight)
    ScenarioBuilder priceEstimate = scenario("Price Estimation")
            .feed(locationFeeder)
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
                            .check(status().is(200))
                            .check(jsonPath("$.estimatedPrice").exists()));

    {
        setUp(
                priceEstimate.injectOpen(
                        // Warm-up: ramp to 50 req/s over 10s
                        rampUsersPerSec(1).to(50).during(Duration.ofSeconds(10)),
                        // Sustain: 100 req/s for 30s
                        constantUsersPerSec(100).during(Duration.ofSeconds(30)),
                        // Spike: 150 req/s for 10s
                        constantUsersPerSec(150).during(Duration.ofSeconds(10))))
                .protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile(95.0).lt(300), // P95 < 300ms
                        global().successfulRequests().percent().gt(95.0), // >95% success rate
                        global().requestsPerSec().gte(50.0) // sustained throughput
                );
    }
}
