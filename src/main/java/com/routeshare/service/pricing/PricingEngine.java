package com.routeshare.service.pricing;

import com.routeshare.model.DriverPricingRule;
import com.routeshare.model.dto.PricingResult;
import com.routeshare.model.enums.IncentiveTier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

    private final List<PricingPolicy> policies;

    @Autowired
    public PricingEngine(List<PricingPolicy> policies) {
        // Sort policies in ascending order of their priority values (lowest priority value goes first)
        this.policies = policies.stream()
                .sorted(Comparator.comparingInt(PricingPolicy::getPriority))
                .collect(Collectors.toList());
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

        // Compute base fare for carpooling (fuel/toll splitting): €2.00 platform fee + €0.50 per kilometer
        double baseFare = 2.00 + (context.getDistanceKm() * 0.50);
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

        // Round fare to 2 decimal places
        double finalFare = Math.round(currentFare * 100.0) / 100.0;
        baseFare = Math.round(baseFare * 100.0) / 100.0;

        return new PricingResult(baseFare, finalFare, appliedPolicies);
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
        double ratePerKm = 0.50;
        for (DriverPricingRule rule : driverRules) {
            if (rule.getType() == com.routeshare.model.enums.PricingRuleType.BASE_RATE_PER_KM) {
                ratePerKm = rule.getValue();
                applied.add(String.format("DriverRule:BASE_RATE_PER_KM(%.2f€/km)", rule.getValue()));
                break;
            }
        }
        double baseFare = 2.00 + (context.getDistanceKm() * ratePerKm);
        double fare = baseFare;

        LocalTime time = context.getDepartureTime() == null ? null : context.getDepartureTime().toLocalTime();
        DayOfWeek day = context.getDepartureTime() == null ? null : context.getDepartureTime().getDayOfWeek();

        for (DriverPricingRule rule : driverRules) {
            switch (rule.getType()) {
                case RUSH_HOUR_SURCHARGE_PCT -> {
                    boolean weekday = day != null && day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
                    boolean rush = time != null && weekday && (
                            (!time.isBefore(LocalTime.of(7, 0)) && !time.isAfter(LocalTime.of(9, 0))) ||
                            (!time.isBefore(LocalTime.of(17, 0)) && !time.isAfter(LocalTime.of(19, 0))));
                    if (rush) {
                        fare *= (1.0 + rule.getValue() / 100.0);
                        applied.add(String.format("DriverRule:RUSH_HOUR_SURCHARGE(%.0f%%)", rule.getValue()));
                    }
                }
                case LATE_NIGHT_FEE_EUR -> {
                    boolean night = time != null &&
                            (!time.isBefore(LocalTime.of(23, 0)) || !time.isAfter(LocalTime.of(5, 0)));
                    if (night) {
                        fare += rule.getValue();
                        applied.add(String.format("DriverRule:LATE_NIGHT_FEE(+%.2f€)", rule.getValue()));
                    }
                }
                case SAME_DESTINATION_DISCOUNT_PCT -> {
                    boolean sameZone = context.getPassengerDestination() != null && context.getDriverDestination() != null
                            && context.getPassengerDestination().trim().equalsIgnoreCase(context.getDriverDestination().trim());
                    if (sameZone) {
                        fare *= (1.0 - rule.getValue() / 100.0);
                        applied.add(String.format("DriverRule:SAME_DESTINATION_DISCOUNT(%.0f%%)", rule.getValue()));
                    }
                }
                case LOYALTY_TIER_DISCOUNT_PCT -> {
                    // Incentive mechanism: reputation tier earns cheaper rides
                    IncentiveTier tier = context.getPassengerTier();
                    boolean loyal = tier == IncentiveTier.GOLD || tier == IncentiveTier.PREMIUM_PRICING;
                    if (loyal) {
                        fare *= (1.0 - rule.getValue() / 100.0);
                        applied.add(String.format("DriverRule:LOYALTY_TIER_DISCOUNT(%.0f%% for %s)", rule.getValue(), tier));
                    }
                }
                case BASE_RATE_PER_KM -> { /* consumed above */ }
            }
        }

        double finalFare = Math.max(0.0, Math.round(fare * 100.0) / 100.0);
        return new PricingResult(Math.round(baseFare * 100.0) / 100.0, finalFare, applied);
    }
}
