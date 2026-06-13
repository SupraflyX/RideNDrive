package com.routeshare.service.pricing;

import com.routeshare.model.dto.PricingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
     * Executes the pricing chain on the given ride context.
     *
     * @param context The ride context data.
     * @return PricingResult containing base fare, final calculated fare, and applied policies audit.
     */
    public PricingResult calculateFare(RideContext context) {
        if (context == null) {
            return new PricingResult(0.0, 0.0, new ArrayList<>());
        }

        // Compute base fare for carpooling (fuel/toll splitting): $2.00 platform fee + $0.50 per kilometer
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
}
