package com.aerolink.ride.controller;

import com.aerolink.ride.dto.request.PriceEstimateRequestDTO;
import com.aerolink.ride.dto.response.PriceEstimateDTO;
import com.aerolink.ride.service.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@Tag(name = "Pricing", description = "Dynamic pricing and fare estimation")
public class PricingController {

    private final PricingService pricingService;

    @PostMapping("/estimate")
    @Operation(summary = "Get price estimate", description = "Calculate an estimated fare for a ride based on distance, demand, and time of day.")
    public ResponseEntity<PriceEstimateDTO> estimate(@Valid @RequestBody PriceEstimateRequestDTO dto) {
        return ResponseEntity.ok(pricingService.estimate(dto));
    }
}
