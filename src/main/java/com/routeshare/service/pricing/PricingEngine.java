package com.routeshare.service.pricing;

import com.routeshare.model.DriverPricingRule;
import com.routeshare.model.dto.PricingResult;
import com.routeshare.model.enums.PricingRuleType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// works out what a ride costs.
//
// two ways in. the default one runs every PricingPolicy in priority order. the other
// takes a driver's own rules and applies those instead. a driver with no rules gets
// the default. either way the result carries a list of what got applied, so a fare
// can always be explained.
@Service
public class PricingEngine {

    // every fare is a flat fee plus so much per km
    private static final double PLATFORM_FEE_EUR = 2.00;
    private static final double DEFAULT_RATE_PER_KM = 0.50;

    private final List<PricingPolicy> policies;

    @Autowired
    public PricingEngine(List<PricingPolicy> policies) {
        // lowest priority number runs first
        this.policies = policies.stream()
                .sorted(Comparator.comparingInt(PricingPolicy::getPriority))
                .toList();
    }

    public PricingResult calculateFare(RideContext context) {
        if (context == null) {
            return new PricingResult(0.0, 0.0, new ArrayList<>());
        }

        double baseFare = baseFare(context, DEFAULT_RATE_PER_KM);
        double currentFare = baseFare;
        List<String> appliedPolicies = new ArrayList<>();

        for (PricingPolicy policy : policies) {
            if (policy.appliesTo(context)) {
                double newFare = policy.applyPolicy(currentFare, context);
                appliedPolicies.add(policy.getClass().getSimpleName());
                currentFare = newFare;
            }
        }

        return new PricingResult(round(baseFare), round(currentFare), appliedPolicies);
    }

    // same thing but using the driver's own rules. each one that fires gets written
    // into the audit list as "DriverRule:TYPE(value)"
    public PricingResult calculateFare(RideContext context, List<DriverPricingRule> driverRules) {
        if (driverRules == null || driverRules.isEmpty()) {
            return calculateFare(context);
        }
        if (context == null) {
            return new PricingResult(0.0, 0.0, new ArrayList<>());
        }

        List<String> applied = new ArrayList<>();

        // driver's own per-km rate if they set one, otherwise ours
        double ratePerKm = DEFAULT_RATE_PER_KM;
        for (DriverPricingRule rule : driverRules) {
            if (rule.getType() == PricingRuleType.BASE_RATE_PER_KM) {
                ratePerKm = rule.getValue();
                applied.add(String.format("DriverRule:BASE_RATE_PER_KM(%.2f€/km)", rule.getValue()));
                break;
            }
        }

        double baseFare = baseFare(context, ratePerKm);
        double fare = baseFare;

        // the conditions themselves live on RideContext, so both paths agree on them
        for (DriverPricingRule rule : driverRules) {
            double value = rule.getValue();
            switch (rule.getType()) {
                case RUSH_HOUR_SURCHARGE_PCT -> {
                    if (context.isRushHour()) {
                        fare *= (1.0 + value / 100.0);
                        applied.add(String.format("DriverRule:RUSH_HOUR_SURCHARGE(%.0f%%)", value));
                    }
                }
                case LATE_NIGHT_FEE_EUR -> {
                    if (context.isLateNight()) {
                        fare += value;
                        applied.add(String.format("DriverRule:LATE_NIGHT_FEE(+%.2f€)", value));
                    }
                }
                case SAME_DESTINATION_DISCOUNT_PCT -> {
                    if (context.isSameDestinationZone()) {
                        fare *= (1.0 - value / 100.0);
                        applied.add(String.format("DriverRule:SAME_DESTINATION_DISCOUNT(%.0f%%)", value));
                    }
                }
                case LOYALTY_TIER_DISCOUNT_PCT -> {
                    if (context.isLoyaltyTier()) {
                        fare *= (1.0 - value / 100.0);
                        applied.add(String.format("DriverRule:LOYALTY_TIER_DISCOUNT(%.0f%% for %s)",
                                value, context.getPassengerTier()));
                    }
                }
                case BASE_RATE_PER_KM -> { } // handled above
            }
        }

        return new PricingResult(round(baseFare), Math.max(0.0, round(fare)), applied);
    }

    private static double baseFare(RideContext context, double ratePerKm) {
        return PLATFORM_FEE_EUR + (context.getDistanceKm() * ratePerKm);
    }

    // money, so always two decimals
    private static double round(double fare) {
        return Math.round(fare * 100.0) / 100.0;
    }
}
