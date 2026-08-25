package com.routeshare.service.pricing;

import org.springframework.stereotype.Component;

// 15% off if the passenger is going to the same place as the driver
@Component
public class SameDestinationZoneDiscount implements PricingPolicy {

    @Override
    public boolean appliesTo(RideContext context) {
        return context != null && context.isSameDestinationZone();
    }

    @Override
    public double applyPolicy(double currentFare, RideContext context) {
        return currentFare * 0.85;
    }

    @Override
    public int getPriority() {
        return 30;
    }
}
