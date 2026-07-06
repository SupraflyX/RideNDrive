package com.routeshare.controller;

import com.routeshare.model.DriverPricingRule;
import com.routeshare.model.DriverTravelRule;
import com.routeshare.model.RideRequest;
import com.routeshare.model.TripOffer;
import com.routeshare.model.User;
import com.routeshare.model.enums.BookingStatus;
import com.routeshare.model.enums.PricingRuleType;
import com.routeshare.model.enums.TravelRuleType;
import com.routeshare.repository.DriverPricingRuleRepository;
import com.routeshare.repository.DriverTravelRuleRepository;
import com.routeshare.repository.RideRequestRepository;
import com.routeshare.repository.TripOfferRepository;
import com.routeshare.repository.UserRepository;
import com.routeshare.service.policy.TravelPolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PolicyController — REST surface of the driver-owned policy engine (FR-15, FR-16).
 *
 *   GET    /api/policies/travel/{driverId}          the driver's travel rules
 *   POST   /api/policies/travel/{driverId}          add a travel rule {type, numericValue?, priority?}
 *   DELETE /api/policies/travel/rule/{ruleId}       remove a travel rule
 *   GET    /api/policies/pricing/{driverId}         the driver's pricing rules
 *   POST   /api/policies/pricing/{driverId}         add a pricing rule {type, value, priority?}
 *   DELETE /api/policies/pricing/rule/{ruleId}      remove a pricing rule
 *   GET    /api/policies/rank/{driverId}/{tripId}   open candidates ranked by the policy engine
 */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final DriverTravelRuleRepository travelRuleRepository;
    private final DriverPricingRuleRepository pricingRuleRepository;
    private final UserRepository userRepository;
    private final TripOfferRepository tripOfferRepository;
    private final RideRequestRepository rideRequestRepository;
    private final TravelPolicyService travelPolicyService;

    @Autowired
    public PolicyController(DriverTravelRuleRepository travelRuleRepository,
                            DriverPricingRuleRepository pricingRuleRepository,
                            UserRepository userRepository,
                            TripOfferRepository tripOfferRepository,
                            RideRequestRepository rideRequestRepository,
                            TravelPolicyService travelPolicyService) {
        this.travelRuleRepository = travelRuleRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.userRepository = userRepository;
        this.tripOfferRepository = tripOfferRepository;
        this.rideRequestRepository = rideRequestRepository;
        this.travelPolicyService = travelPolicyService;
    }

    // ── Travel rules ─────────────────────────────────────────────────

    @GetMapping("/travel/{driverId}")
    public ResponseEntity<List<DriverTravelRule>> listTravelRules(@PathVariable Long driverId) {
        return ResponseEntity.ok(travelRuleRepository.findByDriverIdOrderByPriorityAsc(driverId));
    }

    @PostMapping("/travel/{driverId}")
    public ResponseEntity<?> addTravelRule(@PathVariable Long driverId, @RequestBody Map<String, String> payload) {
        User driver = userRepository.findById(driverId).orElse(null);
        if (driver == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Driver not found."));
        }

        TravelRuleType type;
        try {
            type = TravelRuleType.valueOf(payload.getOrDefault("type", ""));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown travel rule type."));
        }

        Double numericValue = null;
        if (payload.get("numericValue") != null && !payload.get("numericValue").isBlank()) {
            try {
                numericValue = Double.parseDouble(payload.get("numericValue"));
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid numeric value."));
            }
        }
        if (type == TravelRuleType.MIN_PASSENGER_REPUTATION) {
            if (numericValue == null || numericValue < 0 || numericValue > 5) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "MIN_PASSENGER_REPUTATION requires a threshold between 0 and 5."));
            }
        }

        int priority = parsePriority(payload.get("priority"));
        DriverTravelRule saved = travelRuleRepository.save(new DriverTravelRule(driver, type, numericValue, priority));
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/travel/rule/{ruleId}")
    public ResponseEntity<?> deleteTravelRule(@PathVariable Long ruleId) {
        if (!travelRuleRepository.existsById(ruleId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Rule not found."));
        }
        travelRuleRepository.deleteById(ruleId);
        return ResponseEntity.ok(Map.of("deleted", ruleId));
    }

    // ── Pricing rules ────────────────────────────────────────────────

    @GetMapping("/pricing/{driverId}")
    public ResponseEntity<List<DriverPricingRule>> listPricingRules(@PathVariable Long driverId) {
        return ResponseEntity.ok(pricingRuleRepository.findByDriverIdOrderByPriorityAsc(driverId));
    }

    @PostMapping("/pricing/{driverId}")
    public ResponseEntity<?> addPricingRule(@PathVariable Long driverId, @RequestBody Map<String, String> payload) {
        User driver = userRepository.findById(driverId).orElse(null);
        if (driver == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Driver not found."));
        }

        PricingRuleType type;
        try {
            type = PricingRuleType.valueOf(payload.getOrDefault("type", ""));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown pricing rule type."));
        }

        double value;
        try {
            value = Double.parseDouble(payload.getOrDefault("value", ""));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Pricing rules require a numeric value."));
        }
        if (value < 0 || (type != PricingRuleType.LATE_NIGHT_FEE_EUR
                && type != PricingRuleType.BASE_RATE_PER_KM && value > 100)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Value out of range for this rule type."));
        }

        int priority = parsePriority(payload.get("priority"));
        DriverPricingRule saved = pricingRuleRepository.save(new DriverPricingRule(driver, type, value, priority));
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/pricing/rule/{ruleId}")
    public ResponseEntity<?> deletePricingRule(@PathVariable Long ruleId) {
        if (!pricingRuleRepository.existsById(ruleId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Rule not found."));
        }
        pricingRuleRepository.deleteById(ruleId);
        return ResponseEntity.ok(Map.of("deleted", ruleId));
    }

    // ── Candidate ranking (FR-15: "rank the best passengers") ────────

    @GetMapping("/rank/{driverId}/{tripId}")
    public ResponseEntity<?> rankCandidates(@PathVariable Long driverId, @PathVariable Long tripId) {
        TripOffer offer = tripOfferRepository.findById(tripId).orElse(null);
        if (offer == null || offer.getDriver() == null || !offer.getDriver().getId().equals(driverId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Trip not found for this driver."));
        }

        // Candidates: open (unassigned, PENDING) future requests
        List<RideRequest> open = new ArrayList<>();
        for (RideRequest r : rideRequestRepository.findAll()) {
            boolean unassigned = r.getTripOffer() == null;
            boolean pending = r.getStatus() == BookingStatus.PENDING;
            boolean future = r.getPickupTimeWindowStart() != null
                    && r.getPickupTimeWindowStart().isAfter(java.time.LocalDateTime.now().minusHours(1));
            if (unassigned && pending && future) {
                open.add(r);
            }
        }

        List<TravelPolicyService.RankedCandidate> ranked = travelPolicyService.rankCandidates(driverId, offer, open);

        List<Map<String, Object>> body = new ArrayList<>();
        for (TravelPolicyService.RankedCandidate rc : ranked) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("request", rc.getRequest());
            row.put("score", rc.getScore());
            row.put("breakdown", rc.getBreakdown());
            body.add(row);
        }
        return ResponseEntity.ok(body);
    }

    private static int parsePriority(String raw) {
        try {
            return raw == null ? 10 : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 10;
        }
    }
}
