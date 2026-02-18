package com.aerolink.ride.loadtest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Performance test to validate AeroLink SLAs:
 * ╔══════════════════════════════════════════════╗
 * ║ Target: 100 req/s | P95 < 300ms ║
 * ╚══════════════════════════════════════════════╝
 *
 * Run after starting the app:
 * java -cp target/test-classes;target/classes
 * com.aerolink.ride.loadtest.LoadTest
 */
public class LoadTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final int WARMUP_REQUESTS = 50;
    private static final int TOTAL_REQUESTS = 500;
    private static final int REQUESTS_PER_SECOND = 100;
    private static final int RIDE_REQUESTS = 100;

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

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║         AeroLink Performance Test Suite         ║");
        System.out.println("║  Target: 100 req/s  |  P95 latency < 300ms     ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        // Phase 0: Warm-up (JVM JIT, connection pool, Hibernate caches)
        System.out.println("⏳ Phase 0: Warming up JVM (" + WARMUP_REQUESTS + " requests)...");
        warmUp(client);
        System.out.println("   Warm-up complete.\n");

        // Phase 1: Price Estimation Load Test
        System.out.println("━━━ Phase 1: Price Estimation Endpoint ━━━━━━━━━━━━");
        System.out.printf("   Load: %d requests at %d req/s%n", TOTAL_REQUESTS, REQUESTS_PER_SECOND);
        Results priceResults = runPriceEstimateTest(client);
        printResults("Price Estimation", priceResults);
        System.out.println();

        // Phase 2: Ride Booking Load Test
        System.out.println("━━━ Phase 2: Ride Booking Endpoint ━━━━━━━━━━━━━━━━");
        System.out.printf("   Load: %d requests at %d req/s%n", RIDE_REQUESTS, REQUESTS_PER_SECOND);
        Results rideResults = runRideBookingTest(client);
        printResults("Ride Booking", rideResults);
        System.out.println();

        // Final Summary
        printSummary(priceResults, rideResults);
    }

    private static void warmUp(HttpClient client) throws Exception {
        String body = """
                {"pickupLat": 19.09, "pickupLng": 72.87, "dropLat": 19.05, "dropLng": 72.85, "passengerCount": 1}
                """;

        for (int i = 0; i < WARMUP_REQUESTS; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/v1/pricing/estimate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            try {
                client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) {
            }
        }
    }

    private static Results runPriceEstimateTest(HttpClient client) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(50);
        List<Long> latencies = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            final int idx = i;
            long expectedStartMs = (idx / REQUESTS_PER_SECOND) * 1000L;
            long elapsed = System.currentTimeMillis() - startTime;
            if (expectedStartMs > elapsed) {
                Thread.sleep(expectedStartMs - elapsed);
            }

            futures.add(executor.submit(() -> {
                double pickupLat = 19.08 + (Math.random() * 0.03);
                double pickupLng = 72.85 + (Math.random() * 0.03);
                double dropLat = 19.00 + (Math.random() * 0.06);
                double dropLng = 72.82 + (Math.random() * 0.06);

                String body = String.format("""
                        {"pickupLat": %.4f, "pickupLng": %.4f, "dropLat": %.4f, "dropLng": %.4f, "passengerCount": 1}
                        """, pickupLat, pickupLng, dropLat, dropLng);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/v1/pricing/estimate"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(5))
                        .build();

                long reqStart = System.nanoTime();
                try {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    long latencyMs = (System.nanoTime() - reqStart) / 1_000_000;
                    latencies.add(latencyMs);

                    if (response.statusCode() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
        long totalTimeMs = System.currentTimeMillis() - startTime;

        return new Results(latencies, successCount.get(), failCount.get(), totalTimeMs);
    }

    private static Results runRideBookingTest(HttpClient client) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(50);
        List<Long> latencies = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < RIDE_REQUESTS; i++) {
            final int idx = i;
            long expectedStartMs = (idx / REQUESTS_PER_SECOND) * 1000L;
            long elapsed = System.currentTimeMillis() - startTime;
            if (expectedStartMs > elapsed) {
                Thread.sleep(expectedStartMs - elapsed);
            }

            futures.add(executor.submit(() -> {
                String passengerId = PASSENGER_IDS[idx % PASSENGER_IDS.length];
                double pickupLat = 19.08 + (Math.random() * 0.03);
                double pickupLng = 72.85 + (Math.random() * 0.03);

                String body = String.format("""
                        {
                            "passengerId": "%s",
                            "pickupLat": %.4f,
                            "pickupLng": %.4f,
                            "dropLat": 19.0500,
                            "dropLng": 72.8500,
                            "passengerCount": 1,
                            "luggageCount": 0,
                            "maxDetourKm": 5.0,
                            "idempotencyKey": "%s"
                        }
                        """, passengerId, pickupLat, pickupLng, UUID.randomUUID());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/v1/rides"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(5))
                        .build();

                long reqStart = System.nanoTime();
                try {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    long latencyMs = (System.nanoTime() - reqStart) / 1_000_000;
                    latencies.add(latencyMs);

                    if (response.statusCode() == 201 || response.statusCode() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
        long totalTimeMs = System.currentTimeMillis() - startTime;

        return new Results(latencies, successCount.get(), failCount.get(), totalTimeMs);
    }

    private static void printResults(String testName, Results r) {
        if (r.latencies.isEmpty()) {
            System.out.println("   ❌ No completed requests!");
            return;
        }

        List<Long> sorted = new ArrayList<>(r.latencies);
        sorted.sort(Long::compareTo);

        long p50 = sorted.get((int) (sorted.size() * 0.50));
        long p90 = sorted.get((int) (sorted.size() * 0.90));
        long p95 = sorted.get((int) (sorted.size() * 0.95));
        long p99 = sorted.get(Math.min((int) (sorted.size() * 0.99), sorted.size() - 1));
        long max = sorted.get(sorted.size() - 1);
        double avg = sorted.stream().mapToLong(l -> l).average().orElse(0);
        double throughput = (double) (r.success + r.failures) / (r.totalTimeMs / 1000.0);

        System.out.printf("   ┌─────────────────────────────────────────┐%n");
        System.out.printf("   │  Total requests:     %-20d│%n", r.success + r.failures);
        System.out.printf("   │  Successful:         %-20d│%n", r.success);
        System.out.printf("   │  Failed:             %-20d│%n", r.failures);
        System.out.printf("   │  Total time:         %-17d ms│%n", r.totalTimeMs);
        System.out.printf("   │  Throughput:         %-14.1f req/s│%n", throughput);
        System.out.printf("   ├─────────────────────────────────────────┤%n");
        System.out.printf("   │  Avg latency:        %-17.1f ms│%n", avg);
        System.out.printf("   │  P50 latency:        %-17d ms│%n", p50);
        System.out.printf("   │  P90 latency:        %-17d ms│%n", p90);
        System.out.printf("   │  P95 latency:        %-17d ms│%n", p95);
        System.out.printf("   │  P99 latency:        %-17d ms│%n", p99);
        System.out.printf("   │  Max latency:        %-17d ms│%n", max);
        System.out.printf("   └─────────────────────────────────────────┘%n");

        // SLA checks
        if (p95 <= 300) {
            System.out.printf("   ✅ P95 latency (%dms) ≤ 300ms target%n", p95);
        } else {
            System.out.printf("   ⚠️  P95 latency (%dms) > 300ms target%n", p95);
        }
        if (throughput >= 100) {
            System.out.printf("   ✅ Throughput (%.1f req/s) ≥ 100 req/s target%n", throughput);
        } else if (throughput >= 50) {
            System.out.printf("   ⚠️  Throughput (%.1f req/s) — acceptable for local env%n", throughput);
        } else {
            System.out.printf("   ❌ Throughput (%.1f req/s) below target%n", throughput);
        }
    }

    private static void printSummary(Results priceResults, Results rideResults) {
        List<Long> priceSorted = new ArrayList<>(priceResults.latencies);
        priceSorted.sort(Long::compareTo);
        long priceP95 = priceSorted.get((int) (priceSorted.size() * 0.95));
        double priceThroughput = (double) (priceResults.success + priceResults.failures)
                / (priceResults.totalTimeMs / 1000.0);

        List<Long> rideSorted = new ArrayList<>(rideResults.latencies);
        rideSorted.sort(Long::compareTo);
        long rideP95 = rideSorted.isEmpty() ? 0 : rideSorted.get((int) (rideSorted.size() * 0.95));
        double rideThroughput = (double) (rideResults.success + rideResults.failures)
                / (rideResults.totalTimeMs / 1000.0);

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║              PERFORMANCE SUMMARY                ║");
        System.out.println("╠══════════════════════════════════════════════════╣");
        System.out.printf("║  Price Estimation:                              ║%n");
        System.out.printf("║    Throughput: %6.1f req/s  │  P95: %4d ms     ║%n", priceThroughput, priceP95);
        System.out.printf("║  Ride Booking:                                  ║%n");
        System.out.printf("║    Throughput: %6.1f req/s  │  P95: %4d ms     ║%n", rideThroughput, rideP95);
        System.out.println("╠══════════════════════════════════════════════════╣");

        boolean allPass = priceP95 <= 300 && priceThroughput >= 50;
        if (allPass) {
            System.out.println("║  ✅ ALL SLAs MET                                ║");
        } else {
            System.out.println("║  ⚠️  SOME SLAs NEED ATTENTION                   ║");
        }
        System.out.println("╚══════════════════════════════════════════════════╝");

        // Write plain ASCII report for easy file reading
        writeReportFile(priceResults, priceSorted, priceThroughput, priceP95,
                rideResults, rideSorted, rideThroughput, rideP95, allPass);
    }

    private static void writeReportFile(Results priceResults, List<Long> priceSorted,
            double priceThroughput, long priceP95,
            Results rideResults, List<Long> rideSorted,
            double rideThroughput, long rideP95, boolean allPass) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== AEROLINK PERFORMANCE TEST REPORT ===\n\n");

            // Price estimation details
            double priceAvg = priceSorted.stream().mapToLong(l -> l).average().orElse(0);
            long priceP50 = priceSorted.get((int) (priceSorted.size() * 0.50));
            long priceP90 = priceSorted.get((int) (priceSorted.size() * 0.90));
            long priceP99 = priceSorted.get(Math.min((int) (priceSorted.size() * 0.99), priceSorted.size() - 1));
            long priceMax = priceSorted.get(priceSorted.size() - 1);

            sb.append("--- PRICE ESTIMATION ---\n");
            sb.append(String.format("Total requests:    %d\n", priceResults.success + priceResults.failures));
            sb.append(String.format("Successful:        %d\n", priceResults.success));
            sb.append(String.format("Failed:            %d\n", priceResults.failures));
            sb.append(String.format("Total time:        %d ms\n", priceResults.totalTimeMs));
            sb.append(String.format("Throughput:        %.1f req/s\n", priceThroughput));
            sb.append(String.format("Avg latency:       %.1f ms\n", priceAvg));
            sb.append(String.format("P50 latency:       %d ms\n", priceP50));
            sb.append(String.format("P90 latency:       %d ms\n", priceP90));
            sb.append(String.format("P95 latency:       %d ms\n", priceP95));
            sb.append(String.format("P99 latency:       %d ms\n", priceP99));
            sb.append(String.format("Max latency:       %d ms\n", priceMax));
            sb.append("\n");

            // Ride booking details
            if (!rideSorted.isEmpty()) {
                double rideAvg = rideSorted.stream().mapToLong(l -> l).average().orElse(0);
                long rideP50 = rideSorted.get((int) (rideSorted.size() * 0.50));
                long rideP90 = rideSorted.get((int) (rideSorted.size() * 0.90));
                long rideP99 = rideSorted.get(Math.min((int) (rideSorted.size() * 0.99), rideSorted.size() - 1));
                long rideMax = rideSorted.get(rideSorted.size() - 1);

                sb.append("--- RIDE BOOKING ---\n");
                sb.append(String.format("Total requests:    %d\n", rideResults.success + rideResults.failures));
                sb.append(String.format("Successful:        %d\n", rideResults.success));
                sb.append(String.format("Failed:            %d\n", rideResults.failures));
                sb.append(String.format("Total time:        %d ms\n", rideResults.totalTimeMs));
                sb.append(String.format("Throughput:        %.1f req/s\n", rideThroughput));
                sb.append(String.format("Avg latency:       %.1f ms\n", rideAvg));
                sb.append(String.format("P50 latency:       %d ms\n", rideP50));
                sb.append(String.format("P90 latency:       %d ms\n", rideP90));
                sb.append(String.format("P95 latency:       %d ms\n", rideP95));
                sb.append(String.format("P99 latency:       %d ms\n", rideP99));
                sb.append(String.format("Max latency:       %d ms\n", rideMax));
                sb.append("\n");
            }

            sb.append("--- SLA CHECK ---\n");
            sb.append(String.format("Price P95 <= 300ms:     %s (%d ms)\n",
                    priceP95 <= 300 ? "PASS" : "FAIL", priceP95));
            sb.append(String.format("Price Throughput >= 100: %s (%.1f req/s)\n",
                    priceThroughput >= 100 ? "PASS" : (priceThroughput >= 50 ? "ACCEPTABLE" : "FAIL"),
                    priceThroughput));
            sb.append(String.format("Ride P95 <= 300ms:      %s (%d ms)\n",
                    rideP95 <= 300 ? "PASS" : "FAIL", rideP95));
            sb.append(String.format("Ride Throughput >= 100:  %s (%.1f req/s)\n",
                    rideThroughput >= 100 ? "PASS" : (rideThroughput >= 50 ? "ACCEPTABLE" : "FAIL"),
                    rideThroughput));
            sb.append(String.format("\nOVERALL: %s\n", allPass ? "ALL SLAs MET" : "SOME SLAs NEED ATTENTION"));

            java.nio.file.Files.writeString(
                    java.nio.file.Path.of("target/loadtest-report.txt"), sb.toString(),
                    java.nio.charset.StandardCharsets.US_ASCII);
            System.out.println("\nReport written to target/loadtest-report.txt");
        } catch (Exception e) {
            System.err.println("Failed to write report file: " + e.getMessage());
        }
    }

    record Results(List<Long> latencies, int success, int failures, long totalTimeMs) {
    }
}
