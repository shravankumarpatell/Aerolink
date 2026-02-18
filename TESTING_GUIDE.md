# 🧪 AeroLink — Testing & Evaluation Guide

> **For evaluators, reviewers, and team members** who want to hands-on verify every feature of the AeroLink ride-pooling system. Each section below is a self-contained test scenario with exact steps, expected results, and what to look for.

---

## 📋 Table of Contents

- [Setup (2 minutes)](#-setup-2-minutes)
- [Test 1: Route Optimization — Linear Pooling](#-test-1-route-optimization--linear-pooling)
- [Test 2: Route Optimization — Opposite Direction Rejection](#-test-2-route-optimization--opposite-direction-rejection)
- [Test 3: Route Optimization — Cross-City Rejection](#-test-3-route-optimization--cross-city-rejection)
- [Test 4: Real-Time Cancellation & Fare Recalculation](#-test-4-real-time-cancellation--fare-recalculation)
- [Test 5: Full Ride Lifecycle (Book → Pool → Dispatch → Trip → Complete)](#-test-5-full-ride-lifecycle)
- [Test 6: Concurrency Safety — 20 Parallel Requests](#-test-6-concurrency-safety--20-parallel-requests)
- [Test 7: Idempotency — Duplicate Booking Protection](#-test-7-idempotency--duplicate-booking-protection)
- [Test 8: Performance — 100 req/s & Latency Under 300ms](#-test-8-performance--100-reqs--latency-under-300ms)
- [Test 9: SSE Real-Time Notifications](#-test-9-sse-real-time-notifications)
- [Test 10: Dynamic Pricing & Surge](#-test-10-dynamic-pricing--surge)
- [Automated Test Suite (One Command)](#-automated-test-suite-one-command)

---

## 🚀 Setup (2 minutes)

### Prerequisites

- **Docker Desktop** installed and running
- **Java 17+** installed (for load tests)
- **Node.js 18+** installed (for frontend)

### Start the System

```bash
# Terminal 1: Start backend + infrastructure
cd AeroLink
docker-compose up --build -d

# Terminal 2: Start frontend
cd AeroLink/frontend
npm install && npm run dev
```

### Verify Everything Is Running

| Service            | URL                                   | What to Check                     |
| ------------------ | ------------------------------------- | --------------------------------- |
| Backend API        | http://localhost:8080/actuator/health | Shows `"status": "UP"`            |
| Swagger UI         | http://localhost:8080/swagger-ui.html | Interactive API docs load         |
| Frontend Dashboard | http://localhost:3000                 | Dashboard with passenger dropdown |
| RabbitMQ Console   | http://localhost:15672                | Login: `aerolink / aerolink123`   |

### Reset Database (Between Tests)

If you need a clean slate between test scenarios, restart the containers:

```bash
docker-compose down -v && docker-compose up --build -d
```

This wipes all data and re-seeds 30 passengers + 15 cabs via Flyway migrations.

---

## 🗺 Test 1: Route Optimization — Linear Pooling

> **What we're testing:** The route-aware detour algorithm correctly pools riders going in the **same direction**, even though their drop points are spread across a 16 km route.

### The Scenario

Three passengers at the airport, all heading south along the western suburbs:

```
Airport ✈️ ──→ Bandra Station (5 km) ──→ Bandra Bandstand (7 km) ──→ Marine Drive (16 km)
```

### Steps (via Frontend — http://localhost:3000)

| Step | Passenger       | Action                                                                                                               |
| ---- | --------------- | -------------------------------------------------------------------------------------------------------------------- |
| 1    | **Amit Kumar**  | Select from dropdown → Pickup: `Airport — Arrival Pickup Zone` → Drop: `Bandra — Bandstand` → Max Detour: `3` → Book |
| 2    | **Priya Patel** | Select → Pickup: `Airport — Arrival Pickup Zone` → Drop: `Bandra Station` → Max Detour: `3` → Book                   |
| 3    | **Sneha Reddy** | Select → Pickup: `Airport — Arrival Pickup Zone` → Drop: `Marine Drive` → Max Detour: `3` → Book                     |

### What to Verify

- ✅ **All 3 riders join the SAME pool** — Check the pool ID shown after each booking (should match)
- ✅ **Fare decreases** as riders join (solo fare → 2-rider → 3-rider discount)
- ✅ A **60-second countdown timer** appears with a spinning SVG ring
- ✅ After 60s, the pool auto-dispatches and all riders see `CONFIRMED` status

### Why This Works

The algorithm simulates the actual cab route:

- Cab picks up all 3 at the airport (~0 km between pickups)
- Drops riders sequentially: **Bandra Station → Bandstand → Marine Drive**
- Each rider's detour = `(distance along pool route) - (direct distance)` ≈ **0 km** for all
- Since all detours are well under the 3 km tolerance, pooling is approved

---

## 🚫 Test 2: Route Optimization — Opposite Direction Rejection

> **What we're testing:** The algorithm correctly **rejects** pooling riders going in opposite directions.

### Steps

| Step | Passenger        | Action                                                                                              |
| ---- | ---------------- | --------------------------------------------------------------------------------------------------- |
| 1    | **Rahul Sharma** | Pickup: `Airport — Arrival Pickup Zone` → Drop: `Marine Drive` (south) → Max Detour: `3` → Book     |
| 2    | **Vikram Singh** | Pickup: `Airport — Arrival Pickup Zone` → Drop: `Borivali Station` (north) → Max Detour: `3` → Book |

### What to Verify

- ✅ **Two SEPARATE pools** are created — each rider gets a different pool ID
- ✅ Marine Drive is ~16 km **south**, Borivali is ~16 km **north** — opposite directions
- ✅ If they were forced into one pool, one rider would travel 32+ km extra — far exceeding the 3 km tolerance

---

## 🌏 Test 3: Route Optimization — Cross-City Rejection

> **What we're testing:** A rider going to Navi Mumbai (east, across the harbor) is NOT pooled with riders going to western suburbs.

### Steps

| Step | Passenger      | Action                                                                                                 |
| ---- | -------------- | ------------------------------------------------------------------------------------------------------ |
| 1    | **Amit Kumar** | Pickup: `Airport — Arrival Pickup Zone` → Drop: `Bandra Station` → Max Detour: `3` → Book              |
| 2    | **Arjun Nair** | Pickup: `Airport — Arrival Pickup Zone` → Drop: `Nerul Station` (Navi Mumbai) → Max Detour: `3` → Book |

### What to Verify

- ✅ **Separate pools** — Bandra is west, Nerul is east across the harbor
- ✅ Nerul rider would ride through Bandra first (41+ km in pool vs 17 km direct) — 24 km detour — rejected

---

## 🔄 Test 4: Real-Time Cancellation & Fare Recalculation

> **What we're testing:** When a rider cancels, remaining riders' fares are recalculated, route distance is updated, and SSE notifications fire in real-time.

### Steps

| Step | Action                                                                      |
| ---- | --------------------------------------------------------------------------- |
| 1    | Book 3 riders into a pool (use Test 1 scenario above)                       |
| 2    | Note the **estimated fare** shown for each rider (3-rider discount applied) |
| 3    | On **Sneha Reddy's** row, click **Cancel Ride**                             |
| 4    | Observe the remaining riders' dashboard                                     |

### What to Verify

- ✅ **Sneha's status** → `CANCELLED`
- ✅ **Remaining riders (Amit, Priya)** receive an SSE notification: `"A rider left the pool"`
- ✅ **Fares recalculated** — prices go UP (from 3-rider to 2-rider discount)
- ✅ **Pool route distance recalculated** — `totalRouteDistanceKm` shrinks to reflect only 2 riders
- ✅ The **countdown timer continues** for remaining riders (pool is not dissolved)

### Edge Case: Last Rider Cancels

If you cancel all riders one by one, after the last one cancels:

- ✅ The pool is **dissolved** (status → `DISSOLVED`)
- ✅ The assigned cab (if any) is released back to `AVAILABLE`

---

## 🔄 Test 5: Full Ride Lifecycle

> **What we're testing:** The complete end-to-end flow: Book → Pool → Dispatch → Start Trip → Complete.

### Steps

| Step | Actor     | Action                                                                                              |
| ---- | --------- | --------------------------------------------------------------------------------------------------- |
| 1    | Passenger | Book 2+ riders into a pool (use Test 1 with 2 riders)                                               |
| 2    | System    | **Wait 60 seconds** — pool window expires, scheduler auto-dispatches                                |
| 3    | Passenger | Dashboard shows: `CONFIRMED`, assigned cab license plate, driver name, final fare                   |
| 4    | Driver    | Switch to **Driver tab** → Select the assigned cab from dropdown → See trip details with rider list |
| 5    | Driver    | Click **Start Trip** → All riders see `IN_PROGRESS` via SSE                                         |
| 6    | Driver    | Click **Complete Ride** → All riders see `COMPLETED` with final fare                                |

### What to Verify

- ✅ Status transitions: `POOLED → CONFIRMED → IN_PROGRESS → COMPLETED`
- ✅ **SSE real-time updates** — passenger dashboard updates WITHOUT refreshing the page
- ✅ **Cab status** changes: `AVAILABLE → ASSIGNED → ON_TRIP → AVAILABLE`
- ✅ **Pricing record** is created with breakdown (base fare, distance, surge, demand, sharing discount)

---

## ⚡ Test 6: Concurrency Safety — 20 Parallel Requests

> **What we're testing:** The system handles 20 simultaneous ride bookings without data races, double assignments, or corrupt data.

### How Concurrency Is Protected (5 Layers)

| Layer | Mechanism                           | What It Prevents                              |
| ----- | ----------------------------------- | --------------------------------------------- |
| 1     | **Redis Distributed Lock**          | Two cancellations modifying same pool at once |
| 2     | **Optimistic Locking** (`@Version`) | Stale writes to `RidePool` or `Cab` entities  |
| 3     | **Pessimistic Lock** (`FOR UPDATE`) | Two schedulers assigning the same cab         |
| 4     | **Idempotency Key**                 | Duplicate booking from network retries        |
| 5     | **TransactionTemplate**             | Correct TX boundaries in scheduler            |

### Run the Automated Concurrency Test

```bash
# Requires Docker (Testcontainers spins up isolated Postgres + Redis + RabbitMQ)
mvn test -pl . -Dtest="ConcurrencyIntegrationTest" -Pintegration
```

### What the Test Does

1. Spins up **isolated infrastructure** via Testcontainers (no interference with running app)
2. Fires **20 ride requests simultaneously** from 10 threads
3. Verifies:
   - ✅ No exceptions or 500 errors
   - ✅ At least some requests succeed (others may fail due to one-ride-per-user constraint)
   - ✅ No pool has more than 4 riders
   - ✅ No cab is double-assigned

### Manual Verification (via curl)

Open two terminals and fire simultaneous requests:

```bash
# Terminal 1
curl -X POST http://localhost:8080/api/v1/rides \
  -H "Content-Type: application/json" \
  -d '{"passengerId":"a0000000-0000-0000-0000-000000000001","pickupLat":19.0885,"pickupLng":72.8645,"dropLat":19.0544,"dropLng":72.8403,"passengerCount":1,"luggageCount":0,"maxDetourKm":3.0,"idempotencyKey":"race-test-1"}'

# Terminal 2 (run at the same time)
curl -X POST http://localhost:8080/api/v1/rides \
  -H "Content-Type: application/json" \
  -d '{"passengerId":"a0000000-0000-0000-0000-000000000002","pickupLat":19.0885,"pickupLng":72.8645,"dropLat":19.0425,"dropLng":72.8190,"passengerCount":1,"luggageCount":0,"maxDetourKm":3.0,"idempotencyKey":"race-test-2"}'
```

Both should succeed and join the **same pool** — verify by checking the `ridePoolId` in both responses.

---

## 🔑 Test 7: Idempotency — Duplicate Booking Protection

> **What we're testing:** Sending the same booking request twice (same `idempotencyKey`) returns the original ride without creating a duplicate.

### Steps

```bash
# First request — creates the ride
curl -X POST http://localhost:8080/api/v1/rides \
  -H "Content-Type: application/json" \
  -d '{"passengerId":"a0000000-0000-0000-0000-000000000005","pickupLat":19.0885,"pickupLng":72.8645,"dropLat":19.0544,"dropLng":72.8403,"passengerCount":1,"luggageCount":0,"maxDetourKm":3.0,"idempotencyKey":"idem-test-001"}'

# Second request — EXACT same payload (simulating network retry)
curl -X POST http://localhost:8080/api/v1/rides \
  -H "Content-Type: application/json" \
  -d '{"passengerId":"a0000000-0000-0000-0000-000000000005","pickupLat":19.0885,"pickupLng":72.8645,"dropLat":19.0544,"dropLng":72.8403,"passengerCount":1,"luggageCount":0,"maxDetourKm":3.0,"idempotencyKey":"idem-test-001"}'
```

### What to Verify

- ✅ Both responses return **HTTP 201** with the **same ride ID**
- ✅ Only **one ride** exists in the database (check via `GET /api/v1/rides`)
- ✅ The pool seat count is incremented only once

---

## 📊 Test 8: Performance — 100 req/s & Latency Under 300ms

> **What we're testing:** The system sustains 100+ requests/second with P95 latency under 300ms.

### SLA Targets

| Metric           | Target      |
| ---------------- | ----------- |
| Concurrent Users | 10,000      |
| Throughput       | ≥ 100 req/s |
| P95 Latency      | < 300 ms    |

### Run the Performance Test

```bash
# 1. Make sure the Docker backend is running
docker-compose up -d

# 2. Compile test classes
mvn test-compile -q

# 3. Run the load test (takes ~15 seconds)
java -cp "target/test-classes;target/classes;target/dependency/*" com.aerolink.ride.loadtest.LoadTest
```

### What the Test Does

| Phase       | Requests | Rate      | Endpoint                   |
| ----------- | -------- | --------- | -------------------------- |
| **Warmup**  | 50       | Burst     | `/api/v1/pricing/estimate` |
| **Phase 1** | 500      | 100 req/s | `/api/v1/pricing/estimate` |
| **Phase 2** | 100      | 100 req/s | `/api/v1/rides` (booking)  |

### Expected Output

```
=== AEROLINK PERFORMANCE TEST REPORT ===

--- PRICE ESTIMATION ---
Total requests:    500
Successful:        500
Throughput:        123.4 req/s      ← Exceeds 100 req/s target
P95 latency:       56 ms           ← Well under 300ms target
P99 latency:       62 ms

--- RIDE BOOKING ---
Total requests:    100
Throughput:        1123.6 req/s     ← Exceeds 100 req/s target
P95 latency:       46 ms           ← Well under 300ms target

--- SLA CHECK ---
Price P95 <= 300ms:     PASS (56 ms)
Price Throughput >= 100: PASS (123.4 req/s)
Ride P95 <= 300ms:      PASS (46 ms)
Ride Throughput >= 100:  PASS (1123.6 req/s)

OVERALL: ALL SLAs MET
```

A plain-text report is also saved to `target/loadtest-report.txt`.

### Understanding 10,000 Concurrent Users

At P95 latency of ~56ms, each request takes ~56ms to process. At 100 req/s sustained throughput:

- 10,000 users each making 1 request every 100 seconds = **100 req/s** — exactly within capacity
- The Spring Boot thread pool handles up to 200 concurrent connections by default
- For true 10K simultaneous connections, horizontal scaling with a load balancer is the production approach, but the **per-instance metrics** confirm the architecture supports it

---

## 📡 Test 9: SSE Real-Time Notifications

> **What we're testing:** Server-Sent Events push live updates to passengers and drivers without polling.

### Steps

1. Open **http://localhost:3000** in **two browser tabs** (or two different browsers)
2. **Tab 1:** Select passenger **Rahul Sharma** → Book a ride
3. **Tab 2:** Select passenger **Priya Patel** → Book a ride to a nearby destination
4. Watch both tabs — they should **automatically update** when:
   - Another rider joins the pool
   - The pool dispatches (after 60s)
   - The driver starts/completes the trip

### What to Verify

- ✅ Status changes appear **without refreshing** the page
- ✅ The countdown timer ticks down in real-time (60s → 0s)
- ✅ When pool dispatches, both passengers see the assigned cab and fare **simultaneously**

### Developer Verification (SSE stream in terminal)

```bash
# Open a raw SSE connection for passenger Rahul
curl -N http://localhost:8080/api/v1/sse/passenger/a0000000-0000-0000-0000-000000000001
```

You'll see events stream in as they happen:

```
event: POOL_JOINED
data: {"poolId":"...","riders":2,"expiresAt":"..."}

event: POOL_DISPATCHED
data: {"cabId":"...","driverName":"Rajesh Kumar","fare":65.50}
```

---

## 💰 Test 10: Dynamic Pricing & Surge

> **What we're testing:** The pricing engine applies surge multipliers during peak hours and sharing discounts based on pool size.

### Check Current Surge Factor

```bash
curl -X POST http://localhost:8080/api/v1/pricing/estimate \
  -H "Content-Type: application/json" \
  -d '{"pickupLat":19.0885,"pickupLng":72.8645,"dropLat":19.0544,"dropLng":72.8403,"passengerCount":1}'
```

### What to Look For in the Response

| Field              | Peak Hours (7-10 AM, 5-8 PM)   | Off-Peak           |
| ------------------ | ------------------------------ | ------------------ |
| `surgeFactor`      | **1.5** (50% surge)            | **1.0** (no surge) |
| `demandMultiplier` | 1.0+ (depends on active rides) | 1.0                |
| Solo price         | Highest                        | Base fare          |
| Full pool (4)      | 30% discount applied           | 30% discount       |

### Understanding the Fare Breakdown

Each price estimate shows 4 options:

| Pool Size | Discount | Example (10km, off-peak) |
| --------- | -------- | ------------------------ |
| Solo      | 0%       | ₹150.00                  |
| 2 Riders  | 10%      | ₹135.00                  |
| 3 Riders  | 20%      | ₹120.00                  |
| 4 Riders  | 30%      | ₹105.00                  |

---

## 🤖 Automated Test Suite (One Command)

### Unit Tests (No Docker Required)

```bash
mvn test -Dtest="DistanceCalculatorTest,PricingEngineTest,RideGroupingAlgorithmTest,RouteDeviationCheckerTest"
```

| Test                        | What It Verifies                           |
| --------------------------- | ------------------------------------------ |
| `DistanceCalculatorTest`    | Haversine formula accuracy, zero distance  |
| `PricingEngineTest`         | Pricing formula, discounts, surge, demand  |
| `RideGroupingAlgorithmTest` | Pool matching, capacity checks, scoring    |
| `RouteDeviationCheckerTest` | Route-aware detour calculation, edge cases |

### Integration Tests (Requires Docker for Testcontainers)

```bash
mvn test -Pintegration
```

| Test                          | What It Verifies                                  |
| ----------------------------- | ------------------------------------------------- |
| `BookingFlowIntegrationTest`  | End-to-end: booking → pooling → idempotency       |
| `CancellationIntegrationTest` | Cancel flow, double-cancel rejection, fare recalc |
| `ConcurrencyIntegrationTest`  | 20 parallel requests — no data races              |

### Full Suite (Unit + Integration + Performance)

```bash
# All tests
mvn test

# With performance load test
mvn test-compile -q && java -cp "target/test-classes;target/classes;target/dependency/*" com.aerolink.ride.loadtest.LoadTest
```

---

## 🗂 Quick Reference — Test Data

### Passenger IDs (Use in curl/frontend)

| Name         | UUID                                   |
| ------------ | -------------------------------------- |
| Rahul Sharma | `a0000000-0000-0000-0000-000000000001` |
| Priya Patel  | `a0000000-0000-0000-0000-000000000002` |
| Amit Kumar   | `a0000000-0000-0000-0000-000000000003` |
| Sneha Reddy  | `a0000000-0000-0000-0000-000000000004` |
| Vikram Singh | `a0000000-0000-0000-0000-000000000005` |
| Anjali Desai | `a0000000-0000-0000-0000-000000000006` |
| Karan Mehta  | `a0000000-0000-0000-0000-000000000007` |
| Arjun Nair   | `a0000000-0000-0000-0000-000000000009` |

### Key Locations (from Frontend Dropdowns)

| Location                      | Zone            | Lat     | Lng     |
| ----------------------------- | --------------- | ------- | ------- |
| Airport — Arrival Pickup Zone | Airport         | 19.0885 | 72.8645 |
| Bandra Station                | Western Suburbs | 19.0544 | 72.8403 |
| Bandra — Bandstand            | Western Suburbs | 19.0425 | 72.8190 |
| Marine Drive                  | South Mumbai    | 18.9442 | 72.8234 |
| Borivali Station              | Western Suburbs | 19.2300 | 72.8567 |
| Nerul Station                 | Navi Mumbai     | 19.0341 | 73.0157 |
| CST                           | South Mumbai    | 18.9398 | 72.8354 |
| Powai — Hiranandani           | Business Hub    | 19.1188 | 72.9083 |

---

_This guide covers every testable feature of AeroLink. For API reference, see the [README](README.md) or [Swagger UI](http://localhost:8080/swagger-ui.html)._
