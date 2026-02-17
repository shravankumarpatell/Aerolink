-- =============================================================
-- V1: AeroLink Initial Schema
-- Creates core tables for ride pooling system with PostGIS
-- =============================================================

-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;

-- =====================
-- PASSENGERS TABLE
-- =====================
CREATE TABLE passengers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100)    NOT NULL,
    email           VARCHAR(150)    NOT NULL UNIQUE,
    phone           VARCHAR(20)     NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- =====================
-- CABS TABLE
-- =====================
CREATE TABLE cabs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    license_plate       VARCHAR(20)     NOT NULL UNIQUE,
    driver_name         VARCHAR(100)    NOT NULL,
    total_seats         INT             NOT NULL DEFAULT 4,
    luggage_capacity    INT             NOT NULL DEFAULT 4,
    current_lat         DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    current_lng         DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    status              VARCHAR(20)     NOT NULL DEFAULT 'AVAILABLE',
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_cab_status CHECK (status IN ('AVAILABLE', 'ASSIGNED', 'ON_TRIP', 'OFFLINE')),
    CONSTRAINT chk_total_seats CHECK (total_seats > 0 AND total_seats <= 8),
    CONSTRAINT chk_luggage_capacity CHECK (luggage_capacity >= 0)
);

-- =====================
-- RIDE POOLS TABLE
-- =====================
CREATE TABLE ride_pools (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cab_id                      UUID            REFERENCES cabs(id),
    status                      VARCHAR(20)     NOT NULL DEFAULT 'FORMING',
    total_occupied_seats        INT             NOT NULL DEFAULT 0,
    total_luggage               INT             NOT NULL DEFAULT 0,
    total_route_distance_km     DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    version                     BIGINT          NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_pool_status CHECK (status IN ('FORMING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'DISSOLVED')),
    CONSTRAINT chk_occupied_seats CHECK (total_occupied_seats >= 0),
    CONSTRAINT chk_total_luggage CHECK (total_luggage >= 0)
);

-- =====================
-- RIDE REQUESTS TABLE
-- =====================
CREATE TABLE ride_requests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    passenger_id        UUID            NOT NULL REFERENCES passengers(id),
    pickup_lat          DOUBLE PRECISION NOT NULL,
    pickup_lng          DOUBLE PRECISION NOT NULL,
    drop_lat            DOUBLE PRECISION NOT NULL,
    drop_lng            DOUBLE PRECISION NOT NULL,
    passenger_count     INT             NOT NULL DEFAULT 1,
    luggage_count       INT             NOT NULL DEFAULT 0,
    max_detour_km       DOUBLE PRECISION NOT NULL DEFAULT 3.0,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    ride_pool_id        UUID            REFERENCES ride_pools(id),
    estimated_price     DOUBLE PRECISION,
    idempotency_key     VARCHAR(64)     NOT NULL UNIQUE,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ride_status CHECK (status IN ('PENDING', 'POOLED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_passenger_count CHECK (passenger_count > 0 AND passenger_count <= 8),
    CONSTRAINT chk_luggage_count CHECK (luggage_count >= 0),
    CONSTRAINT chk_max_detour CHECK (max_detour_km > 0)
);

-- =====================
-- PRICING RECORDS TABLE
-- =====================
CREATE TABLE pricing_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_request_id     UUID            NOT NULL REFERENCES ride_requests(id) UNIQUE,
    base_price          DOUBLE PRECISION NOT NULL,
    distance_km         DOUBLE PRECISION NOT NULL,
    demand_multiplier   DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    sharing_discount    DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    surge_factor        DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    final_price         DOUBLE PRECISION NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- =====================
-- INDEXES
-- =====================

-- Spatial: find available cabs near a location
CREATE INDEX idx_cabs_status ON cabs(status);

-- Fast lookup of active ride requests
CREATE INDEX idx_ride_requests_status ON ride_requests(status);

-- Pool membership lookups
CREATE INDEX idx_ride_requests_pool ON ride_requests(ride_pool_id);

-- Time-window queries for demand calculation
CREATE INDEX idx_ride_requests_created ON ride_requests(created_at);

-- Active pool queries
CREATE INDEX idx_ride_pools_status ON ride_pools(status);

-- Cab assignment lookups in pool
CREATE INDEX idx_ride_pools_cab ON ride_pools(cab_id);
