package com.routeshare.service.pricing;

import org.springframework.stereotype.Component;

/**
 * SameDestinationZoneDiscount implements a 15% discount when the passenger shares the driver's exact destination.
 *
 * Demonstrates:
 * - Strategy Pattern implementation: Spatial matching rules providing cost incentives.
 */
@Component
public class SameDestinationZoneDiscount implements PricingPolicy {

    @Override
    public boolean appliesTo(RideContext context) {
        if (context == null || context.getPassengerDestination() == null || context.getDriverDestination() == null) {
            return false;
        }
        return context.getPassengerDestination().trim().equalsIgnoreCase(context.getDriverDestination().trim());
    }

    @Override
    public double applyPolicy(double currentFare, RideContext context) {
        // Apply 15% discount
        return currentFare * 0.85;
    }

    @Override
    public int getPriority() {
        return 30;
    }
}
