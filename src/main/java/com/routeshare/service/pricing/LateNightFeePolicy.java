package com.routeshare.service.pricing;

import org.springframework.stereotype.Component;

/**
 * LateNightFeePolicy implements a 30% surcharge for trip departures scheduled late at night.
 *
 * Demonstrates:
 * - Strategy Pattern implementation: Adjusts the fare based on late night intervals.
 *   The midnight-spanning window itself is defined once on RideContext.
 */
@Component
public class LateNightFeePolicy implements PricingPolicy {

    @Override
    public boolean appliesTo(RideContext context) {
        return context != null && context.isLateNight();
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
