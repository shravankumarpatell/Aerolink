package com.aerolink.ride.service;

import com.aerolink.ride.algorithm.DistanceCalculator;
import com.aerolink.ride.algorithm.PricingEngine;
import com.aerolink.ride.dto.request.PriceEstimateRequestDTO;
import com.aerolink.ride.dto.response.PriceEstimateDTO;
import com.aerolink.ride.entity.PricingRecord;
import com.aerolink.ride.entity.RideRequest;
import com.aerolink.ride.enums.CabStatus;
import com.aerolink.ride.enums.RideStatus;
import com.aerolink.ride.repository.CabRepository;
import com.aerolink.ride.repository.PricingRecordRepository;
import com.aerolink.ride.repository.RideRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

        private final PricingRecordRepository pricingRecordRepository;
        private final RideRequestRepository rideRequestRepository;
        private final CabRepository cabRepository;

        @Value("${aerolink.pricing.base-fare-per-km}")
        private double baseFarePerKm;

        @Value("${aerolink.pricing.discount-per-co-rider}")
        private double discountPerCoRider;

        @Value("${aerolink.pricing.min-sharing-discount}")
        private double minSharingDiscount;

        @Value("${aerolink.pricing.demand-threshold}")
        private double demandThreshold;

        @Value("${aerolink.pricing.demand-sensitivity}")
        private double demandSensitivity;

        @Value("${aerolink.pricing.peak-surge-factor}")
        private double peakSurgeFactor;

        @Value("${aerolink.pricing.off-peak-surge-factor}")
        private double offPeakSurgeFactor;

        @Value("${aerolink.pricing.peak-hours-start}")
        private int peakHoursStart;

        @Value("${aerolink.pricing.peak-hours-end}")
        private int peakHoursEnd;

        @Value("${aerolink.pricing.evening-peak-start}")
        private int eveningPeakStart;

        @Value("${aerolink.pricing.evening-peak-end}")
        private int eveningPeakEnd;

        /**
         * Get a price estimate showing fare range for 1-4 riders (solo to full pool).
         */
        public PriceEstimateDTO estimate(PriceEstimateRequestDTO dto) {
                double distanceKm = DistanceCalculator.calculateKm(
                                dto.getPickupLat(), dto.getPickupLng(),
                                dto.getDropLat(), dto.getDropLng());

                long activeRequests = rideRequestRepository.countActiveRequestsSince(
                                List.of(RideStatus.PENDING, RideStatus.POOLED, RideStatus.CONFIRMED),
                                LocalDateTime.now().minusMinutes(15));

                long availableCabs = cabRepository.findByStatus(CabStatus.AVAILABLE).size();

                // Calculate price for each pool size (1 to 4)
                List<PriceEstimateDTO.PriceOption> prices = new ArrayList<>();
                String[] labels = { "Solo Ride", "2 Riders", "3 Riders", "Full Pool (4)" };

                double demandMultiplier = 0;
                double surgeFactor = 0;

                for (int poolSize = 1; poolSize <= 4; poolSize++) {
                        PricingEngine.PricingResult result = PricingEngine.calculate(
                                        distanceKm, baseFarePerKm, activeRequests, availableCabs,
                                        poolSize, discountPerCoRider, minSharingDiscount,
                                        demandThreshold, demandSensitivity, peakSurgeFactor, offPeakSurgeFactor,
                                        peakHoursStart, peakHoursEnd, eveningPeakStart, eveningPeakEnd);

                        if (poolSize == 1) {
                                demandMultiplier = result.demandMultiplier();
                                surgeFactor = result.surgeFactor();
                        }

                        prices.add(PriceEstimateDTO.PriceOption.builder()
                                        .poolSize(poolSize)
                                        .label(labels[poolSize - 1])
                                        .sharingDiscount(result.sharingDiscount())
                                        .price(result.finalPrice())
                                        .build());
                }

                return PriceEstimateDTO.builder()
                                .distanceKm(distanceKm)
                                .baseFarePerKm(baseFarePerKm)
                                .demandMultiplier(demandMultiplier)
                                .surgeFactor(surgeFactor)
                                .prices(prices)
                                .notes("Final fare depends on actual pool size at dispatch time (60s window)")
                                .build();
        }

        /**
         * Calculate and persist pricing for a ride request within a pool.
         * Called at dispatch time when actual pool size is known.
         */
        @Transactional
        public PricingRecord calculateAndPersist(RideRequest rideRequest, int poolSize) {
                double distanceKm = DistanceCalculator.calculateKm(
                                rideRequest.getPickupLat(), rideRequest.getPickupLng(),
                                rideRequest.getDropLat(), rideRequest.getDropLng());

                long activeRequests = rideRequestRepository.countActiveRequestsSince(
                                List.of(RideStatus.PENDING, RideStatus.POOLED, RideStatus.CONFIRMED),
                                LocalDateTime.now().minusMinutes(15));

                long availableCabs = cabRepository.findByStatus(CabStatus.AVAILABLE).size();

                PricingEngine.PricingResult result = PricingEngine.calculate(
                                distanceKm, baseFarePerKm, activeRequests, availableCabs,
                                poolSize, discountPerCoRider, minSharingDiscount,
                                demandThreshold, demandSensitivity, peakSurgeFactor, offPeakSurgeFactor,
                                peakHoursStart, peakHoursEnd, eveningPeakStart, eveningPeakEnd);

                // Check if pricing record already exists (update if so)
                // PricingRecord stores PER-PERSON fare for transparency
                PricingRecord record = pricingRecordRepository.findByRideRequestId(rideRequest.getId())
                                .map(existing -> {
                                        existing.setBasePrice(result.basePrice());
                                        existing.setDistanceKm(result.distanceKm());
                                        existing.setDemandMultiplier(result.demandMultiplier());
                                        existing.setSharingDiscount(result.sharingDiscount());
                                        existing.setSurgeFactor(result.surgeFactor());
                                        existing.setFinalPrice(result.finalPrice());
                                        return pricingRecordRepository.save(existing);
                                })
                                .orElseGet(() -> {
                                        PricingRecord newRecord = PricingRecord.builder()
                                                        .rideRequest(rideRequest)
                                                        .basePrice(result.basePrice())
                                                        .distanceKm(result.distanceKm())
                                                        .demandMultiplier(result.demandMultiplier())
                                                        .sharingDiscount(result.sharingDiscount())
                                                        .surgeFactor(result.surgeFactor())
                                                        .finalPrice(result.finalPrice())
                                                        .build();
                                        return pricingRecordRepository.save(newRecord);
                                });

                // Booking total = per-person fare × number of passengers in this booking
                // e.g. ₹150/person × 2 passengers = ₹300 total for this booking
                int paxCount = rideRequest.getPassengerCount();
                double bookingTotal = result.finalPrice() * paxCount;
                rideRequest.setEstimatedPrice(bookingTotal);

                log.info("Pricing for ride {}: ₹{}/person × {} pax = ₹{} total (poolSize={}, dist={}km)",
                                rideRequest.getId(), result.finalPrice(), paxCount, bookingTotal, poolSize, distanceKm);
                return record;
        }

        /**
         * Recalculate pricing for all active members of a pool (after cancellation).
         */
        @Transactional
        public void recalculatePoolPricing(List<RideRequest> activeRequests, int newPoolSize) {
                for (RideRequest request : activeRequests) {
                        calculateAndPersist(request, newPoolSize);
                }
                log.info("Recalculated pricing for {} riders with new pool size {}", activeRequests.size(),
                                newPoolSize);
        }
}
