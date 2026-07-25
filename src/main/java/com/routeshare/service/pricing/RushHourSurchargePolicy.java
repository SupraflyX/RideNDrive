package com.routeshare.service.pricing;

import org.springframework.stereotype.Component;

/**
 * RushHourSurchargePolicy implements a 25% price surcharge during commuter peak hours.
 *
 * Demonstrates:
 * - Strategy Pattern implementation: Intercepts and adjusts the cumulative fare based on temporal constraints.
 * - Weekday Peak checks (Rigor & Formality): the peak window itself is defined once on
 *   RideContext, so this policy and the driver-composed rule set (FR-16) always agree.
 */
@Component
public class RushHourSurchargePolicy implements PricingPolicy {

    @Override
    public boolean appliesTo(RideContext context) {
        return context != null && context.isRushHour();
    }

    @Override
    public double applyPolicy(double currentFare, RideContext context) {
        // Apply +25% surcharge
        return currentFare * 1.25;
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
