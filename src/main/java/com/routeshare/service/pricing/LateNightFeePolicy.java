package com.routeshare.service.pricing;

import org.springframework.stereotype.Component;
import java.time.LocalTime;

/**
 * LateNightFeePolicy implements a 30% surcharge for trip departures scheduled late at night.
 *
 * Demonstrates:
 * - Strategy Pattern implementation: Adjusts the fare based on late night intervals.
 */
@Component
public class LateNightFeePolicy implements PricingPolicy {

    private static final LocalTime NIGHT_START = LocalTime.of(23, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(5, 0);

    @Override
    public boolean appliesTo(RideContext context) {
        if (context == null || context.getDepartureTime() == null) {
            return false;
        }
        LocalTime time = context.getDepartureTime().toLocalTime();
        // Spans midnight: time >= 23:00 OR time <= 05:00
        return !time.isBefore(NIGHT_START) || !time.isAfter(NIGHT_END);
    }

    @Override
    public double applyPolicy(double currentFare, RideContext context) {
        // Apply +30% fee
        return currentFare * 1.30;
    }

    @Override
    public int getPriority() {
        return 20;
    }
}
