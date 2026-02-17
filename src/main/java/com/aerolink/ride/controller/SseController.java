package com.aerolink.ride.controller;

import com.aerolink.ride.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    /**
     * SSE stream for a passenger — receives real-time updates about their ride.
     */
    @GetMapping(value = "/passenger/{passengerId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter passengerStream(@PathVariable UUID passengerId) {
        return sseService.registerPassenger(passengerId);
    }

    /**
     * SSE stream for a driver (by cab ID) — receives trip assignments and updates.
     */
    @GetMapping(value = "/driver/{cabId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter driverStream(@PathVariable UUID cabId) {
        return sseService.registerDriver(cabId);
    }
}
