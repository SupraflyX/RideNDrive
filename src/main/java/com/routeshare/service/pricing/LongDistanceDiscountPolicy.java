package com.routeshare.service.pricing;

import org.springframework.stereotype.Component;

/* long trips shouldn't be priced like a taxi. over 100km we cap the fare at roughly
   what sharing fuel and tolls would actually cost */
@Component
public class LongDistanceDiscountPolicy implements PricingPolicy {

    @Override
    public boolean appliesTo(RideContext context) {
        return context != null && context.getDistanceKm() > 100;
    }

    @Override
    public double applyPolicy(double currentFare, RideContext context) {
        double distance = context.getDistanceKm();
        
        // about 0.045 per km on top of the base fee
        double targetFare = 2.00 + (distance * 0.045);
        
        // only ever bring the fare down, never up
        return Math.min(currentFare, targetFare);
    }

    @Override
    public int getPriority() {
        return 5; // Apply first, establishing the baseline before surcharges and other discounts
    }
}
