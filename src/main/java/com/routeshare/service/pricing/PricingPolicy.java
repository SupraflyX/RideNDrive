package com.routeshare.service.pricing;

/**
 * PricingPolicy defines the Strategy Pattern interface for implementing carpool pricing rules.
 *
 * Demonstrates:
 * - Strategy Pattern (GoF Behavioral / Ch. 9): Encapsulating algorithm rules into interchangeable objects.
 * - Open/Closed Principle (SE Principle 5 / Conway's Law): System is open to new pricing policies, but closed to modifications of the pricing engine.
 * - Non-parametric pricing policies as requested by the professor (runtime policy customization vs. static formulas).
 */
public interface PricingPolicy {

    /**
     * Determines whether the policy is active for a given ride context.
     *
     * @param context The current state variables of the carpool request.
     * @return true if the policy should apply, false otherwise.
     */
    boolean appliesTo(RideContext context);

    /**
     * Applies the policy to adjust the current fare.
     *
     * @param currentFare The cumulative fare calculated by previous policies.
     * @param context The ride context metadata.
     * @return The updated fare amount.
     */
    double applyPolicy(double currentFare, RideContext context);

    /**
     * Gets the priority weight of the policy. Lower values represent higher priority (evaluated earlier in the chain).
     *
     * @return priority index.
     */
    int getPriority();
}
