package com.routeshare.service.pricing;

import org.springframework.stereotype.Component;

// +30% for late night departures
@Component
public class LateNightFeePolicy implements PricingPolicy {

    @Override
    public boolean appliesTo(RideContext context) {
        return context != null && context.isLateNight();
    }

    @Override
    public double applyPolicy(double currentFare, RideContext context) {
        return currentFare * 1.30;
    }

    @Override
    public int getPriority() {
        return 20;
    }
}
