# 🛫 AeroLink — Smart Airport Ride Pooling System

A production-grade backend system that groups airport-bound passengers into shared cabs using **real-time spatial clustering**, **dynamic pricing**, and **event-driven dispatch**. Built with Spring Boot 3, PostgreSQL + PostGIS, Redis, and RabbitMQ — containerized with Docker Compose for one-command startup.

> **Live Dashboard**: A real-time frontend is included with countdown timers, fare estimation, SSE-driven updates, and driver/passenger views.

---

## 📋 Table of Contents

- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Quick Start](#-quick-start)
- [API Documentation](#-api-documentation)
- [Core Algorithms](#-core-algorithms)
- [Concurrency & Safety](#-concurrency--safety)
- [Database Schema](#-database-schema)
- [Sample Test Data](#-sample-test-data)
- [Testing](#-testing)
- [Frontend Dashboard](#-frontend-dashboard)
- [Configuration](#-configuration)
- [Assumptions](#-assumptions)
- [Project Structure](#-project-structure)

---

## 🔧 Tech Stack

| Layer             | Technology                         | Purpose                             |
| ----------------- | ---------------------------------- | ----------------------------------- |
| **Language**      | Java 17                            | Core application                    |
| **Framework**     | Spring Boot 3.2.5                  | Web, DI, scheduling, event handling |
| **Database**      | PostgreSQL 15 + PostGIS            | Persistent storage + geospatial     |
| **Caching/Locks** | Redis 7                            | Distributed locks, rate limiting    |
| **Message Queue** | RabbitMQ 3.13                      | Async event notification            |
| **ORM**           | Spring Data JPA (Hibernate 6.4)    | Entity persistence                  |
| **DB Migration**  | Flyway                             | Schema versioning                   |
| **API Docs**      | SpringDoc OpenAPI 2.5 (Swagger UI) | Interactive API documentation       |
| **Real-time**     | Server-Sent Events (SSE)           | Live updates to passengers/drivers  |
| **Testing**       | JUnit 5 + Mockito + Testcontainers | Unit, integration, performance      |
| **Performance**   | Gatling                            | Load testing at scale               |
| **Build**         | Maven 3.9+                         | Build & dependency management       |
| **Container**     | Docker + Docker Compose            | One-command infrastructure          |
| **Frontend**      | Vanilla JS + Vite + CSS            | Real-time dashboard                 |

---

## 🏗 Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                     Client (Browser / REST Consumer)               │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ HTTP + SSE
┌──────────────────────────────▼─────────────────────────────────────┐
│                       REST API Layer (Controllers)                 │
│  ┌──────────────┐  ┌─────────────┐  ┌──────────┐  ┌──────────┐   │
│  │RideController│  │CabController│  │ Pricing  │  │   SSE    │   │
│  │  /api/v1/rides│  │ /api/v1/cabs│  │Controller│  │Controller│   │
│  └──────┬───────┘  └──────┬──────┘  └────┬─────┘  └────┬─────┘   │
└─────────┼─────────────────┼──────────────┼──────────────┼─────────┘
          │                 │              │              │
┌─────────▼─────────────────▼──────────────▼──────────────▼─────────┐
│                        Service Layer                               │
│  ┌──────────────────┐  ┌──────────┐  ┌──────────────────────────┐ │
│  │RidePoolingService│  │CabService│  │CancellationService       │ │
│  │  Pool lifecycle  │  │ Trip mgmt│  │  Redis lock + refund     │ │
│  └────────┬─────────┘  └────┬─────┘  └──────────┬───────────────┘ │
│           │                 │                    │                  │
│  ┌────────▼─────────────────▼────────────────────▼────────┐       │
│  │              Algorithm Layer (Pure functions)           │       │
│  │  RideGroupingAlgorithm  │  PricingEngine               │       │
│  │  DistanceCalculator     │  RouteDeviationChecker        │       │
│  └────────────────────────────────────────────────────────┘       │
│                                                                    │
│  ┌──────────────────────────────┐  ┌──────────────────────┐       │
│  │  PoolDispatchScheduler       │  │  SseService           │       │
│  │  @Scheduled every 5s         │  │  Real-time push       │       │
│  │  Pool window expiry → assign │  │  Passenger + Driver   │       │
│  └──────────────────────────────┘  └──────────────────────┘       │
└──────┬──────────────┬────────────────┬────────────────────────────┘
       │              │                │
  ┌────▼────┐   ┌─────▼─────┐   ┌─────▼─────┐
  │PostgreSQL│   │   Redis   │   │ RabbitMQ  │
  │ + PostGIS│   │  Dist.    │   │  Event    │
  │  Schema  │   │  Locks    │   │  Fanout   │
  └──────────┘   └───────────┘   └───────────┘
```

### Ride Lifecycle Flow

```
Book Ride → POOLED → Pool Timer (60s) → DISPATCHING → Cab Assigned → CONFIRMED
                                                                         │
                                                                    Start Trip
                                                                         │
                                                                   IN_PROGRESS
                                                                         │
                                                                   Complete Ride
                                                                         │
                                                                    COMPLETED
```

---

## 🚀 Quick Start

### Prerequisites

- **Docker Desktop** installed and running
- **Java 17+** (only for local development without Docker)
- **Node.js 18+** (only for frontend dev server)

### Option A: Docker Compose (Recommended — One Command)

```bash
# Clone the repository
git clone https://github.com/<your-username>/AeroLink.git
cd AeroLink

# Start everything (backend + PostgreSQL + Redis + RabbitMQ)
docker-compose up --build
```

Wait ~15 seconds for Spring Boot to start. You'll see:

```
Started AeroLinkApplication in 12.9 seconds
Startup cleanup complete — scheduler now active
```

### Option B: Local Development

```bash
# 1. Start infrastructure only
docker-compose up postgres redis rabbitmq -d

# 2. Build and run the backend
mvn clean package -DskipTests
java -jar target/aerolink-ride-1.0.0.jar

# 3. Start the frontend (separate terminal)
cd frontend
npm install
npm run dev
```

### Access Points

| Service                | URL                                   | Credentials            |
| ---------------------- | ------------------------------------- | ---------------------- |
| **Backend API**        | http://localhost:8080                 | —                      |
| **Swagger UI**         | http://localhost:8080/swagger-ui.html | —                      |
| **OpenAPI Spec**       | http://localhost:8080/api-docs        | —                      |
| **Frontend Dashboard** | http://localhost:3000                 | —                      |
| **Health Check**       | http://localhost:8080/actuator/health | —                      |
| **RabbitMQ Console**   | http://localhost:15672                | aerolink / aerolink123 |

---

## 📖 API Documentation

Full interactive docs available at **Swagger UI**: `http://localhost:8080/swagger-ui.html`

### Rides API — `/api/v1/rides`

| Method | Endpoint                                 | Description                                  |
| ------ | ---------------------------------------- | -------------------------------------------- |
| `POST` | `/api/v1/rides`                          | Book a ride (joins/creates pool, 60s window) |
| `GET`  | `/api/v1/rides`                          | List all rides                               |
| `GET`  | `/api/v1/rides/{id}`                     | Get ride details by ID                       |
| `POST` | `/api/v1/rides/{id}/cancel`              | Cancel a ride (recalculates pool fares)      |
| `GET`  | `/api/v1/rides/pool/{poolId}`            | Get pool details with all riders             |
| `GET`  | `/api/v1/rides/passenger/{id}/dashboard` | Passenger dashboard (active + history)       |
| `GET`  | `/api/v1/rides/driver/{cabId}/dashboard` | Driver dashboard (active pool + riders)      |

### Cabs API — `/api/v1/cabs`

| Method  | Endpoint                          | Description                             |
| ------- | --------------------------------- | --------------------------------------- |
| `GET`   | `/api/v1/cabs`                    | List all cabs with status               |
| `GET`   | `/api/v1/cabs/{id}`               | Get cab details                         |
| `PATCH` | `/api/v1/cabs/{id}/location`      | Update cab GPS coordinates              |
| `POST`  | `/api/v1/cabs/{id}/start-trip`    | Driver starts trip (pool → IN_PROGRESS) |
| `POST`  | `/api/v1/cabs/{id}/complete-ride` | Complete all rides in pool              |

### Pricing API — `/api/v1/pricing`

| Method | Endpoint                   | Description                             |
| ------ | -------------------------- | --------------------------------------- |
| `POST` | `/api/v1/pricing/estimate` | Estimate fare (distance, demand, surge) |

### SSE (Real-time Events) — `/api/v1/sse`

| Method | Endpoint                              | Description                       |
| ------ | ------------------------------------- | --------------------------------- |
| `GET`  | `/api/v1/sse/passenger/{passengerId}` | SSE stream for passenger updates  |
| `GET`  | `/api/v1/sse/driver/{cabId}`          | SSE stream for driver assignments |

**SSE Event Types:**

| Event             | Target    | Payload                              |
| ----------------- | --------- | ------------------------------------ |
| `POOL_JOINED`     | Passenger | Pool info after joining              |
| `POOL_DISPATCHED` | Passenger | Assigned cab, driver, fare           |
| `POOL_WAITING`    | Passenger | No cab yet, retrying                 |
| `RIDE_STARTED`    | Passenger | Trip has begun                       |
| `RIDE_COMPLETED`  | Passenger | Trip finished, final fare            |
| `RIDE_CANCELLED`  | Passenger | Ride cancelled                       |
| `TRIP_ASSIGNED`   | Driver    | New pool assignment with pickup info |
| `TRIP_CANCELLED`  | Driver    | Pool dissolved / cancelled           |

### Sample Requests & Responses

#### Book a Ride

```bash
curl -X POST http://localhost:8080/api/v1/rides \
  -H "Content-Type: application/json" \
  -d '{
    "passengerId": "a0000000-0000-0000-0000-000000000001",
    "pickupLat": 19.0896,
    "pickupLng": 72.8656,
    "dropLat": 19.1197,
    "dropLng": 72.8464,
    "passengerCount": 1,
    "luggageCount": 1,
    "maxDetourKm": 3.0,
    "idempotencyKey": "booking-001"
  }'
```

**Response (201 Created):**

```json
{
  "id": "bff87cd6-0771-4060-9085-a047d37ffbfd",
  "passengerId": "a0000000-0000-0000-0000-000000000001",
  "passengerName": "Rahul Sharma",
  "pickupLat": 19.0896,
  "pickupLng": 72.8656,
  "dropLat": 19.1197,
  "dropLng": 72.8464,
  "passengerCount": 1,
  "luggageCount": 1,
  "status": "POOLED",
  "ridePoolId": "f36aafc3-fc69-4f2b-a88e-928b2890ce73",
  "estimatedPrice": 87.93,
  "createdAt": "2026-02-17T17:49:21"
}
```

#### Get Price Estimate

```bash
curl -X POST http://localhost:8080/api/v1/pricing/estimate \
  -H "Content-Type: application/json" \
  -d '{
    "pickupLat": 19.0896,
    "pickupLng": 72.8656,
    "dropLat": 19.1197,
    "dropLng": 72.8464
  }'
```

**Response:**

```json
{
  "distanceKm": 3.91,
  "baseFare": 58.62,
  "surgeFactor": 1.5,
  "demandMultiplier": 1.0,
  "estimatedFares": {
    "solo": 87.93,
    "twoRiders": 79.14,
    "threeRiders": 70.34,
    "fullPool": 61.55
  }
}
```

#### Cancel a Ride

```bash
curl -X POST http://localhost:8080/api/v1/rides/{rideId}/cancel \
  -H "Content-Type: application/json" \
  -d '{ "reason": "Flight delayed" }'
```

#### Get Passenger Dashboard

```bash
curl http://localhost:8080/api/v1/rides/passenger/a0000000-0000-0000-0000-000000000001/dashboard
```

---

## 🧠 Core Algorithms

### 1. Ride Grouping — Greedy Spatial Clustering

**File:** `algorithm/RideGroupingAlgorithm.java`
**Complexity:** `O(m × k)` per request, where `m` = candidate pools, `k` = riders per pool

```
For each new RideRequest:
  1. Query FORMING pools within 0.5 km radius        → O(log n) via PostGIS index
  2. Filter by seat + luggage capacity                → O(m)
  3. Validate detour tolerance for ALL existing riders → O(m × k)
  4. Score remaining by: pickup proximity (40%)        → O(m)
                        + route overlap (40%)
                        + sharing bonus (-2 per rider)
  5. Select lowest-scored pool or create new           → O(1)
```

**In practice:** `m ≤ 10 pools, k ≤ 4 riders` → effectively **O(1)** per request.

### 2. Dynamic Pricing Engine

**File:** `algorithm/PricingEngine.java`
**Complexity:** `O(1)` per calculation

```
finalPrice = baseFare/km × distance × surgeFactor × demandMultiplier × sharingDiscount

Parameters (configurable in application.yml):
┌────────────────────┬───────────────────────────────────────────────────────┐
│ baseFare           │ ₹15/km                                               │
│ surgeFactor        │ 1.5× during peak (7–10 AM, 5–8 PM), else 1.0×       │
│ demandMultiplier   │ max(1.0, 1 + (activeRequests/availableCabs - 0.7)×0.5)│
│ sharingDiscount    │ 1 - (poolSize - 1) × 0.10   (floor: 0.60)           │
└────────────────────┴───────────────────────────────────────────────────────┘

Discount breakdown:
  Solo ride    → 0% discount (full fare)
  2 riders     → 10% discount each
  3 riders     → 20% discount each
  4 riders     → 30% discount each (max)
```

### 3. Haversine Distance Calculator

**File:** `algorithm/DistanceCalculator.java`
**Complexity:** `O(1)`

Calculates great-circle distance between two GPS coordinates using the Haversine formula. Earth radius = 6,371 km.

### 4. Route Deviation Checker

**File:** `algorithm/RouteDeviationChecker.java`
**Complexity:** `O(k)` per check, where `k` = existing riders in pool

Validates that adding a new passenger's route doesn't detour **any** existing rider beyond their `maxDetourKm` tolerance (default: 3 km). Uses triangle inequality with the Haversine formula.

### 5. Pool Dispatch Scheduler

**File:** `service/PoolDispatchScheduler.java`
**Complexity:** `O(p × c)` per cycle, where `p` = ready pools, `c` = available cabs

```
Every 5 seconds:
  1. Find FORMING pools with expired window OR full (4 seats) → O(1) DB query
  2. For each pool:
     a. Acquire pessimistic lock (SELECT FOR UPDATE)          → O(1)
     b. Find nearest available cab within radius              → O(c) via PostGIS
     c. Lock and assign cab                                   → O(1)
     d. Calculate final fares for all riders                   → O(k)
     e. SSE notify all passengers + driver                    → O(k)
```

---

## 🔒 Concurrency & Safety

| Layer | Mechanism                                  | Purpose                               | Implementation                             |
| ----- | ------------------------------------------ | ------------------------------------- | ------------------------------------------ |
| 1     | **Redis Distributed Lock**                 | Prevent concurrent pool modifications | `RedisLockRegistry` in CancellationService |
| 2     | **Optimistic Locking** (`@Version`)        | Detect stale concurrent writes        | On `RidePool`, `Cab` entities              |
| 3     | **Pessimistic Lock** (`SELECT FOR UPDATE`) | Prevent double cab assignment         | `findByIdWithLock()` in repositories       |
| 4     | **Idempotency Key**                        | Prevent duplicate bookings on retry   | Unique key per ride request                |
| 5     | **TransactionTemplate**                    | Programmatic TX for scheduler         | Avoids Spring proxy self-invocation bug    |

---

## 🗃 Database Schema

**5 tables** managed via Flyway migrations (`V1__init_schema.sql`, `V2__seed_data.sql`, `V3__pool_window_refactor.sql`):

```
┌─────────────┐       ┌──────────────┐       ┌───────────────┐
│  passengers │       │  ride_pools  │       │     cabs      │
├─────────────┤       ├──────────────┤       ├───────────────┤
│ id (PK)     │       │ id (PK)      │◄──────│ id (PK)       │
│ name        │       │ cab_id (FK)  │       │ license_plate │
│ email       │       │ status       │       │ driver_name   │
│ phone       │       │ total_seats  │       │ total_seats   │
│ created_at  │       │ total_luggage│       │ luggage_cap   │
└──────┬──────┘       │ pickup_lat/lng│      │ current_lat/lng│
       │              │ drop_lat/lng │       │ status        │
       │              │ window_exp_at│       │ version       │
       │              │ dispatched_at│       └───────────────┘
       │              │ version      │
       │              └──────┬───────┘
       │                     │
       │   ┌─────────────────▼──────┐   ┌──────────────────┐
       └──►│    ride_requests       │──►│ pricing_records  │
            ├───────────────────────┤   ├──────────────────┤
            │ id (PK)               │   │ id (PK)          │
            │ passenger_id (FK)     │   │ ride_request_id  │
            │ ride_pool_id (FK)     │   │ base_fare        │
            │ pickup_lat/lng        │   │ distance_km      │
            │ drop_lat/lng          │   │ surge_factor     │
            │ passenger_count       │   │ demand_mult      │
            │ luggage_count         │   │ sharing_discount │
            │ max_detour_km         │   │ final_price      │
            │ status                │   │ pool_size        │
            │ estimated_price       │   └──────────────────┘
            │ idempotency_key (UQ)  │
            └───────────────────────┘
```

**Key Indexes:**

| Index                                | Purpose                        |
| ------------------------------------ | ------------------------------ |
| `idx_cabs_status`                    | Fast available cab lookups     |
| `idx_ride_requests_status`           | Active request queries         |
| `idx_ride_requests_created`          | Time-window demand calculation |
| `idx_ride_pools_status`              | Forming pool queries           |
| `idx_ride_pools_window`              | Scheduler: expired/full pools  |
| `idx_ride_requests_passenger_status` | One-active-ride-per-user check |

---

## 📊 Sample Test Data

The database is auto-seeded via Flyway migrations on startup. No manual setup required.

### Passengers (30 seeded)

| ID (UUID)                   | Name            | Category           |
| --------------------------- | --------------- | ------------------ |
| `a0000000-...-000000000001` | Rahul Sharma    | Frequent flyer     |
| `a0000000-...-000000000002` | Priya Patel     | Frequent flyer     |
| `a0000000-...-000000000003` | Amit Kumar      | Frequent flyer     |
| `a0000000-...-000000000010` | Meera Joshi     | Frequent flyer     |
| `a0000000-...-000000000011` | Rohan Gupta     | Business traveller |
| `a0000000-...-000000000020` | Shruti Agarwal  | Business traveller |
| `a0000000-...-000000000021` | Deepak Saxena   | Occasional         |
| `a0000000-...-000000000030` | Isha Chatterjee | Occasional         |

### Cabs (15 seeded — all 4 seats / 4 luggage)

| ID (UUID)                   | License Plate | Driver         | Location           | Status    |
| --------------------------- | ------------- | -------------- | ------------------ | --------- |
| `c0000000-...-000000000001` | MH-01-AA-1001 | Rajesh Kumar   | Airport Terminal 2 | AVAILABLE |
| `c0000000-...-000000000002` | MH-01-AB-1002 | Suresh Yadav   | Airport Terminal 2 | AVAILABLE |
| `c0000000-...-000000000009` | MH-01-BA-2001 | Santosh Bhosle | Airport Terminal 1 | AVAILABLE |
| `c0000000-...-000000000013` | MH-01-CA-3001 | Nilesh Sawant  | Andheri            | AVAILABLE |
| `c0000000-...-000000000015` | MH-01-CC-3003 | Ashok Mane     | Andheri            | AVAILABLE |

### Named Locations (Frontend Dropdowns)

| Location                               | Lat     | Lng     |
| -------------------------------------- | ------- | ------- |
| Mumbai Airport — Terminal 2 (Intl)     | 19.0896 | 72.8656 |
| Mumbai Airport — Terminal 1 (Domestic) | 19.0990 | 72.8740 |
| Andheri Station (West)                 | 19.1197 | 72.8464 |
| Bandra-Kurla Complex (BKC)             | 19.0660 | 72.8710 |
| Juhu Beach                             | 19.0948 | 72.8267 |
| Powai (IIT Gate)                       | 19.1334 | 72.9133 |
| Lower Parel                            | 18.9932 | 72.8312 |
| CST (Chhatrapati Shivaji Terminus)     | 18.9398 | 72.8354 |
| Nariman Point                          | 18.9256 | 72.8242 |
| Thane Station                          | 19.1860 | 72.9752 |

---

## 🧪 Testing

```bash
# Run ALL tests (unit + integration)
mvn test

# Run ONLY unit tests (no Docker required)
mvn test -Punit

# Run ONLY integration tests (requires Docker for Testcontainers)
mvn test -Pintegration

# Run Gatling performance tests
mvn gatling:test -Pperf
```

### Unit Tests (4 tests — no external dependencies)

| Test Class                  | Covers                                       |
| --------------------------- | -------------------------------------------- |
| `DistanceCalculatorTest`    | Haversine formula, zero distance, edge cases |
| `PricingEngineTest`         | Pricing formula, discounts, surge, demand    |
| `RideGroupingAlgorithmTest` | Pool matching, capacity checks, scoring      |
| `RouteDeviationCheckerTest` | Detour tolerance, boundary conditions        |

### Integration Tests (3 tests — Testcontainers)

| Test Class                    | Covers                                            |
| ----------------------------- | ------------------------------------------------- |
| `BookingFlowIntegrationTest`  | E2E: booking → pooling → idempotency              |
| `CancellationIntegrationTest` | Cancel flow, double-cancel rejection, fare recalc |
| `ConcurrencyIntegrationTest`  | 20 parallel requests — no data races              |

### Performance / Load Test

| Test Class | Covers                                              |
| ---------- | --------------------------------------------------- |
| `LoadTest` | 100 concurrent bookings, throughput + latency check |

---

## 🖥 Frontend Dashboard

The frontend is a Vite-powered vanilla JS app with a real-time dashboard for both passengers and drivers.

**Start (separate from backend):**

```bash
cd frontend
npm install
npm run dev
# Open http://localhost:3000
```

**Features:**

- 🔽 **Passenger Selector** — 15 seeded passengers in dropdown
- 💰 **Fare Estimator** — Solo, 2-rider, 3-rider, and full pool prices
- 🚗 **Ride Booking** — Location dropdowns with named Mumbai locations
- ⏱ **Live Countdown** — 60-second pool window timer with SVG ring
- 📡 **SSE Updates** — Real-time status changes (pooled → dispatched → in-progress → completed)
- 🗺 **Nearby Cabs** — Live cab availability around pickup location
- 🚕 **Driver Panel** — Active trip details, start/complete trip controls
- 📋 **Ride History** — Past rides with status and fare details

---

## ⚙ Configuration

All config is in `src/main/resources/application.yml`:

### Pricing Parameters

| Parameter                | Default | Description                        |
| ------------------------ | ------- | ---------------------------------- |
| `base-fare-per-km`       | ₹15.0   | Base fare per kilometer            |
| `discount-per-co-rider`  | 0.10    | 10% discount per additional rider  |
| `min-sharing-discount`   | 0.60    | Maximum 40% discount (floor 0.60)  |
| `peak-surge-factor`      | 1.5     | Surge multiplier during peak hours |
| `peak-hours-start/end`   | 7–10    | Morning peak hours                 |
| `evening-peak-start/end` | 17–20   | Evening peak hours                 |

### Pooling Parameters

| Parameter              | Default | Description                        |
| ---------------------- | ------- | ---------------------------------- |
| `search-radius-km`     | 0.5     | Radius to find forming pools       |
| `max-detour-km`        | 3.0     | Maximum route deviation per rider  |
| `max-pool-size`        | 4       | Maximum passengers per pool        |
| `pool-window-seconds`  | 60      | Seconds to wait before dispatching |
| `dispatch-interval-ms` | 5000    | Scheduler polling interval         |

### Concurrency Parameters

| Parameter              | Default | Description                         |
| ---------------------- | ------- | ----------------------------------- |
| `lock-timeout-seconds` | 10      | Redis distributed lock timeout      |
| `optimistic-retry-max` | 3       | Max retries on optimistic lock fail |

---

## 📌 Assumptions

1. **Airport context** — Rides originate from/near airport terminal areas with predictable pickup zones
2. **Pool capacity** — Maximum 4 passengers per pool (configurable)
3. **Cab spec** — All cabs have 4 seats and 4 luggage slots
4. **Detour tolerance** — Default 3 km max route deviation per passenger
5. **Currency** — All pricing in Indian Rupees (₹ / INR)
6. **Peak hours** — 7–10 AM and 5–8 PM (configurable)
7. **Pool window** — 60-second window to gather riders before dispatch
8. **Single active ride** — Each passenger can have only one active ride at a time
9. **Cab assignment** — Nearest available cab is assigned at dispatch time (not at booking)
10. **Idempotency** — Duplicate booking requests with the same key return the original ride

---

## 📂 Project Structure

```
AeroLink/
├── docker-compose.yml              # Infrastructure (PG, Redis, RabbitMQ) + app
├── Dockerfile                      # Multi-stage build (maven → JRE runtime)
├── pom.xml                         # Maven config with test profiles
│
├── frontend/                       # Vite + Vanilla JS dashboard
│   ├── index.html                  # Main HTML
│   ├── css/style.css               # Dark theme styling
│   ├── js/
│   │   ├── app.js                  # Dashboard logic, SSE, countdown
│   │   ├── api.js                  # REST client wrapper
│   │   └── locations.js            # Named Mumbai locations
│   ├── package.json
│   └── vite.config.js              # Dev proxy to :8080
│
└── src/
    ├── main/
    │   ├── java/com/aerolink/ride/
    │   │   ├── AeroLinkApplication.java
    │   │   ├── algorithm/           # Pure algorithms
    │   │   │   ├── DistanceCalculator.java
    │   │   │   ├── PricingEngine.java
    │   │   │   ├── RideGroupingAlgorithm.java
    │   │   │   └── RouteDeviationChecker.java
    │   │   ├── config/              # Spring config
    │   │   ├── controller/          # REST controllers (4)
    │   │   ├── dto/                 # Request/Response DTOs
    │   │   │   ├── request/
    │   │   │   ├── response/
    │   │   │   └── event/
    │   │   ├── entity/              # JPA entities (5)
    │   │   ├── enums/               # CabStatus, PoolStatus, RideStatus
    │   │   ├── exception/           # Custom exceptions + global handler
    │   │   ├── messaging/           # RabbitMQ pub/sub
    │   │   ├── repository/          # Spring Data JPA repos
    │   │   └── service/             # Business logic + scheduler
    │   └── resources/
    │       ├── application.yml      # All configuration
    │       └── db/migration/        # Flyway migrations
    │           ├── V1__init_schema.sql
    │           ├── V2__seed_data.sql
    │           └── V3__pool_window_refactor.sql
    └── test/
        └── java/com/aerolink/ride/
            ├── algorithm/           # Unit tests (4)
            ├── integration/         # Integration tests (3)
            └── loadtest/            # Performance test (1)
```

---

## 📄 License

MIT

---

_Built with ☕ and 🛫 for the airport ride-sharing challenge._
