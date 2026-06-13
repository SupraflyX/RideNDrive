package com.routeshare.service;

import com.routeshare.model.enums.IncentiveTier;
import com.routeshare.model.dto.PricingResult;
import com.routeshare.service.pricing.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PricingEngineTest validates the Chain of Responsibility pricing engine logic.
 *
 * Demonstrates:
 * - Strategy & Chain of Responsibility verification (GoF patterns / Ch. 9).
 * - Equivalence Partitioning: Verifying behavior across time periods (rush-hour, late-night, normal-hours)
 *   and spatial contexts (matching vs. mismatching destination zones).
 */
public class PricingEngineTest {

    private PricingEngine pricingEngine;

    @BeforeEach
    public void setUp() {
        // Instantiate the policy strategies and initialize the engine chain manually for clean unit isolation
        PricingPolicy rushHour = new RushHourSurchargePolicy();
        PricingPolicy lateNight = new LateNightFeePolicy();
        PricingPolicy sameZone = new SameDestinationZoneDiscount();

        pricingEngine = new PricingEngine(Arrays.asList(rushHour, lateNight, sameZone));
    }

    @Test
    public void testNoPoliciesApply() {
        // Tuesday at 12:00 PM (noon) - Not rush hour, not late night
        // Passenger goes ZoneC -> ZoneD, Driver goes ZoneA -> ZoneB (different destinations)
        RideContext context = new RideContext(
                LocalDateTime.of(2026, 6, 2, 12, 0), // Tuesday
                "ZoneC", "ZoneD",
                "ZoneA", "ZoneB",
                5.0, IncentiveTier.STANDARD,
                10.0 // Distance 10 km
        );

        PricingResult result = pricingEngine.calculateFare(context);

        // Base fare: $2.00 flat + $0.50 * 10 km = $7.00
        assertEquals(7.00, result.getBaseFare());
        assertEquals(7.00, result.getFinalFare());
        assertTrue(result.getAppliedPolicies().isEmpty());
    }

    @Test
    public void testRushHourSurcharge() {
        // Tuesday at 8:00 AM - Weekday morning rush hour
        RideContext context = new RideContext(
                LocalDateTime.of(2026, 6, 2, 8, 0), // Tuesday
                "ZoneC", "ZoneD",
                "ZoneA", "ZoneB",
                5.0, IncentiveTier.STANDARD,
                10.0
        );

        PricingResult result = pricingEngine.calculateFare(context);

        // Base fare: $7.00
        // Rush hour: +25% -> $7.00 * 1.25 = $8.75
        assertEquals(7.00, result.getBaseFare());
        assertEquals(8.75, result.getFinalFare());
        assertEquals(1, result.getAppliedPolicies().size());
        assertTrue(result.getAppliedPolicies().contains("RushHourSurchargePolicy"));
    }

    @Test
    public void testLateNightFee() {
        // Tuesday at 1:00 AM (Late night fee window)
        RideContext context = new RideContext(
                LocalDateTime.of(2026, 6, 2, 1, 0),
                "ZoneC", "ZoneD",
                "ZoneA", "ZoneB",
                5.0, IncentiveTier.STANDARD,
                10.0
        );

        PricingResult result = pricingEngine.calculateFare(context);

        // Base: $7.00
        // Night surcharge: +30% -> $7.00 * 1.30 = $9.10
        assertEquals(7.00, result.getBaseFare());
        assertEquals(9.10, result.getFinalFare());
        assertEquals(1, result.getAppliedPolicies().size());
        assertTrue(result.getAppliedPolicies().contains("LateNightFeePolicy"));
    }

    @Test
    public void testSameZoneDiscount() {
        // Tuesday at 12:00 PM (No time surcharges)
        // Destinations are identical ("ZoneB")
        RideContext context = new RideContext(
                LocalDateTime.of(2026, 6, 2, 12, 0),
                "ZoneC", "ZoneB",
                "ZoneA", "ZoneB",
                5.0, IncentiveTier.STANDARD,
                10.0
        );

        PricingResult result = pricingEngine.calculateFare(context);

        // Base: $7.00
        // Same destination: -15% -> $7.00 * 0.85 = $5.95
        assertEquals(7.00, result.getBaseFare());
        assertEquals(5.95, result.getFinalFare());
        assertEquals(1, result.getAppliedPolicies().size());
        assertTrue(result.getAppliedPolicies().contains("SameDestinationZoneDiscount"));
    }

    @Test
    public void testMultiplePoliciesStack() {
        // Weekday morning rush hour AND same destination zone discount
        // Tuesday at 8:00 AM
        RideContext context = new RideContext(
                LocalDateTime.of(2026, 6, 2, 8, 0),
                "ZoneC", "ZoneB",
                "ZoneA", "ZoneB",
                5.0, IncentiveTier.STANDARD,
                10.0
        );

        PricingResult result = pricingEngine.calculateFare(context);

        // Base: $7.00
        // 1. Rush Hour (+25%): $7.00 * 1.25 = $8.75 (Priority 10)
        // 2. Same Zone (-15%): $8.75 * 0.85 = $7.4375 -> rounded to $7.44 (Priority 30)
        assertEquals(7.00, result.getBaseFare());
        assertEquals(7.44, result.getFinalFare());
        assertEquals(2, result.getAppliedPolicies().size());
        assertEquals("RushHourSurchargePolicy", result.getAppliedPolicies().get(0));
        assertEquals("SameDestinationZoneDiscount", result.getAppliedPolicies().get(1));
    }
}
