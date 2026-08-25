package com.routeshare.service.pricing;

import org.springframework.stereotype.Component;

// +25% during weekday peak hours
@Component
public class RushHourSurchargePolicy implements PricingPolicy {

    @Override
    public boolean appliesTo(RideContext context) {
        return context != null && context.isRushHour();
    }

    @Override
    public double applyPolicy(double currentFare, RideContext context) {
        return currentFare * 1.25;
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
