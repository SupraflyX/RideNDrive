package com.routeshare.model.dto;

import java.util.List;

/**
 * PricingResult is a Data Transfer Object (DTO) containing output from the Pricing Engine rule evaluation.
 *
 * Demonstrates:
 * - Data Transfer Object Pattern: Returning a structured audit trail of pricing rule applications.
 * - Separation of Concerns: Decoupling the pricing calculation results from JPA entities.
 */
public class PricingResult {

    private double baseFare;
    private double finalFare;
    private List<String> appliedPolicies;

    public PricingResult() {
    }

    public PricingResult(double baseFare, double finalFare, List<String> appliedPolicies) {
        this.baseFare = baseFare;
        this.finalFare = finalFare;
        this.appliedPolicies = appliedPolicies;
    }

    public double getBaseFare() {
        return baseFare;
    }

    public void setBaseFare(double baseFare) {
        this.baseFare = baseFare;
    }

    public double getFinalFare() {
        return finalFare;
    }

    public void setFinalFare(double finalFare) {
        this.finalFare = finalFare;
    }

    public List<String> getAppliedPolicies() {
        return appliedPolicies;
    }

    public void setAppliedPolicies(List<String> appliedPolicies) {
        this.appliedPolicies = appliedPolicies;
    }
}
