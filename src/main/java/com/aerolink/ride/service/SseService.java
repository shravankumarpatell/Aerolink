package com.aerolink.ride.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-Sent Events service for real-time notifications.
 * Manages SSE connections per passenger and per driver (cab).
 */
@Slf4j
@Service
public class SseService {

    private final Map<UUID, SseEmitter> passengerEmitters = new ConcurrentHashMap<>();
    private final Map<UUID, SseEmitter> driverEmitters = new ConcurrentHashMap<>();

    // Track which pool each passenger/driver belongs to for pool-wide broadcasts
    private final Map<UUID, Set<UUID>> poolToPassengers = new ConcurrentHashMap<>();

    /**
     * Register an SSE connection for a passenger.
     */
    public SseEmitter registerPassenger(UUID passengerId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        passengerEmitters.put(passengerId, emitter);

        emitter.onCompletion(() -> {
            passengerEmitters.remove(passengerId);
            log.debug("SSE connection closed for passenger {}", passengerId);
        });
        emitter.onTimeout(() -> {
            passengerEmitters.remove(passengerId);
            log.debug("SSE connection timed out for passenger {}", passengerId);
        });
        emitter.onError(e -> {
            passengerEmitters.remove(passengerId);
            log.debug("SSE connection error for passenger {}", passengerId);
        });

        log.info("SSE connection registered for passenger {}", passengerId);
        return emitter;
    }

    /**
     * Register an SSE connection for a driver (by cab ID).
     */
    public SseEmitter registerDriver(UUID cabId) {
        SseEmitter emitter = new SseEmitter(0L);
        driverEmitters.put(cabId, emitter);

        emitter.onCompletion(() -> {
            driverEmitters.remove(cabId);
            log.debug("SSE connection closed for driver {}", cabId);
        });
        emitter.onTimeout(() -> {
            driverEmitters.remove(cabId);
            log.debug("SSE connection timed out for driver {}", cabId);
        });
        emitter.onError(e -> {
            driverEmitters.remove(cabId);
            log.debug("SSE connection error for driver {}", cabId);
        });

        log.info("SSE connection registered for driver (cab) {}", cabId);
        return emitter;
    }

    /**
     * Track a passenger in a pool for broadcast purposes.
     */
    public void trackPassengerInPool(UUID poolId, UUID passengerId) {
        poolToPassengers.computeIfAbsent(poolId, k -> ConcurrentHashMap.newKeySet()).add(passengerId);
    }

    /**
     * Remove passenger tracking from a pool.
     */
    public void removePassengerFromPool(UUID poolId, UUID passengerId) {
        Set<UUID> passengers = poolToPassengers.get(poolId);
        if (passengers != null) {
            passengers.remove(passengerId);
            if (passengers.isEmpty()) {
                poolToPassengers.remove(poolId);
            }
        }
    }

    /**
     * Send event to a specific passenger.
     */
    public void emitToPassenger(UUID passengerId, String eventType, Object data) {
        SseEmitter emitter = passengerEmitters.get(passengerId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventType)
                        .data(data));
            } catch (IOException e) {
                passengerEmitters.remove(passengerId);
                log.debug("Failed to send SSE to passenger {}: {}", passengerId, e.getMessage());
            }
        }
    }

    /**
     * Send event to a specific driver (by cab ID).
     */
    public void emitToDriver(UUID cabId, String eventType, Object data) {
        SseEmitter emitter = driverEmitters.get(cabId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventType)
                        .data(data));
            } catch (IOException e) {
                driverEmitters.remove(cabId);
                log.debug("Failed to send SSE to driver {}: {}", cabId, e.getMessage());
            }
        }
    }

    /**
     * Broadcast event to all passengers in a pool.
     */
    public void emitToPool(UUID poolId, String eventType, Object data) {
        Set<UUID> passengers = poolToPassengers.get(poolId);
        if (passengers != null) {
            for (UUID passengerId : passengers) {
                emitToPassenger(passengerId, eventType, data);
            }
        }
    }

    /**
     * Clean up pool tracking when pool is completed/dissolved.
     */
    public void cleanupPool(UUID poolId) {
        poolToPassengers.remove(poolId);
    }
}
