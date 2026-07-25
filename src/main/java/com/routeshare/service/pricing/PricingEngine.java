package com.routeshare.service.pricing;

import com.routeshare.model.DriverPricingRule;
import com.routeshare.model.dto.PricingResult;
import com.routeshare.model.enums.PricingRuleType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PricingEngine coordinates the execution of the Chain of Responsibility for carpool fare calculation.
 *
 * Design Patterns:
 * - Chain of Responsibility (GoF Behavioral / Ch. 9): Rules are chained and executed in priority order.
 * - Strategy (GoF Behavioral): Concrete rules implementing the PricingPolicy interface.
 * - Factory Method (Spring DI / Ch. 9): Dynamically discovers all active policies at runtime.
 *
 * Course Syllabus Concepts:
 * - Anticipation of Change (SE Principle 5): Adding a new pricing rule does not alter this engine.
 * - Low Coupling & High Cohesion: The engine only knows about the PricingPolicy abstraction, not details of specific surcharge rules.
 */
@Service
public class PricingEngine {

    /** €2.00 platform fee plus a per-kilometre rate is the shape of every fare. */
    private static final double PLATFORM_FEE_EUR = 2.00;
    private static final double DEFAULT_RATE_PER_KM = 0.50;

    private final List<PricingPolicy> policies;

    @Autowired
    public PricingEngine(List<PricingPolicy> policies) {
        // Sort policies in ascending order of their priority values (lowest priority value goes first)
        this.policies = policies.stream()
                .sorted(Comparator.comparingInt(PricingPolicy::getPriority))
                .toList();
    }

    /**
     * Executes the platform's default pricing chain on the given ride context.
     *
     * @param context The ride context data.
     * @return PricingResult containing base fare, final calculated fare, and applied policies audit.
     */
    public PricingResult calculateFare(RideContext context) {
        if (context == null) {
            return new PricingResult(0.0, 0.0, new ArrayList<>());
        }

        // Base fare for carpooling (fuel/toll splitting): platform fee + per-kilometre rate
        double baseFare = baseFare(context, DEFAULT_RATE_PER_KM);
        double currentFare = baseFare;
        List<String> appliedPolicies = new ArrayList<>();

        // Chain of Responsibility traversal
        for (PricingPolicy policy : policies) {
            if (policy.appliesTo(context)) {
                double newFare = policy.applyPolicy(currentFare, context);
                appliedPolicies.add(policy.getClass().getSimpleName());
                currentFare = newFare;
            }
        }

        return new PricingResult(round(baseFare), round(currentFare), appliedPolicies);
    }

    /**
     * FR-16 — driver-composed pricing policy (rule/policy customizability).
     *
     * When the driver has defined her own enabled pricing rules, the engine
     * interprets that ordered rule set instead of the platform default chain;
     * every applied rule is written to the audit trail as
     * "DriverRule:TYPE(value)". With no rules, the default chain applies —
     * documented fallback semantics.
     */
    public PricingResult calculateFare(RideContext context, List<DriverPricingRule> driverRules) {
        if (driverRules == null || driverRules.isEmpty()) {
            return calculateFare(context);
        }
        if (context == null) {
            return new PricingResult(0.0, 0.0, new ArrayList<>());
        }

        List<String> applied = new ArrayList<>();

        // Base rate: the driver's own €/km if she defined one, platform default otherwise
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

        // Conditions come from RideContext — the same definitions the platform chain uses.
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
                    // Incentive mechanism: reputation tier earns cheaper rides
                    if (context.isLoyaltyTier()) {
                        fare *= (1.0 - value / 100.0);
                        applied.add(String.format("DriverRule:LOYALTY_TIER_DISCOUNT(%.0f%% for %s)",
                                value, context.getPassengerTier()));
                    }
                }
                case BASE_RATE_PER_KM -> { /* consumed above */ }
            }
        }

        return new PricingResult(round(baseFare), Math.max(0.0, round(fare)), applied);
    }

    /** Every fare starts as the platform fee plus a per-kilometre rate. */
    private static double baseFare(RideContext context, double ratePerKm) {
        return PLATFORM_FEE_EUR + (context.getDistanceKm() * ratePerKm);
    }

    /** Fares are money: two decimal places, always. */
    private static double round(double fare) {
        return Math.round(fare * 100.0) / 100.0;
    }
}
