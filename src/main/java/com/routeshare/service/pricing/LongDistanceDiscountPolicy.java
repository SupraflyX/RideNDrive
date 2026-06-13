package com.routeshare.service.pricing;

import org.springframework.stereotype.Component;

/**
 * LongDistanceDiscountPolicy implements a scaling discount for long distance rides.
 * Trips over 100km receive a steep reduction to better reflect carpooling cost-sharing (fuel/tolls)
 * rather than taxi-like per-kilometer rates.
 */
@Component
public class LongDistanceDiscountPolicy implements PricingPolicy {

    @Override
    public boolean appliesTo(RideContext context) {
        return context != null && context.getDistanceKm() > 100;
    }

    @Override
    public double applyPolicy(double currentFare, RideContext context) {
        double distance = context.getDistanceKm();
        
        // Target fare for long distance carpooling is roughly 0.045 EUR per km + 2.00 base fee
        // For a 1250km trip, this results in approximately 58.25 EUR
        double targetFare = 2.00 + (distance * 0.045);
        
        // We only apply this policy to reduce the fare, not increase it.
        return Math.min(currentFare, targetFare);
    }

    @Override
    public int getPriority() {
        return 5; // Apply first, establishing the baseline before surcharges and other discounts
    }
}
