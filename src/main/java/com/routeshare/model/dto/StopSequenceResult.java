package com.routeshare.model.dto;

import java.util.List;

/**
 * StopSequenceResult is a Data Transfer Object (DTO) returning the results of the Stop Planning Algorithm.
 *
 * Demonstrates:
 * - Data Transfer Object Pattern: Decoupling algorithmic execution results from entity models.
 * - Separation of Concerns: Capturing routing details and constraint feasibility metadata (e.g. violation reason).
 */
public class StopSequenceResult {

    private List<String> sequence;
    private int totalTimeMinutes;
    private double totalDistanceKm;
    private boolean feasible;
    private String violationReason;

    public StopSequenceResult() {
    }

    public StopSequenceResult(List<String> sequence, int totalTimeMinutes, double totalDistanceKm, boolean feasible, String violationReason) {
        this.sequence = sequence;
        this.totalTimeMinutes = totalTimeMinutes;
        this.totalDistanceKm = totalDistanceKm;
        this.feasible = feasible;
        this.violationReason = violationReason;
    }

    public List<String> getSequence() {
        return sequence;
    }

    public void setSequence(List<String> sequence) {
        this.sequence = sequence;
    }

    public int getTotalTimeMinutes() {
        return totalTimeMinutes;
    }

    public void setTotalTimeMinutes(int totalTimeMinutes) {
        this.totalTimeMinutes = totalTimeMinutes;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public void setTotalDistanceKm(double totalDistanceKm) {
        this.totalDistanceKm = totalDistanceKm;
    }

    public boolean isFeasible() {
        return feasible;
    }

    public void setFeasible(boolean feasible) {
        this.feasible = feasible;
    }

    public String getViolationReason() {
        return violationReason;
    }

    public void setViolationReason(String violationReason) {
        this.violationReason = violationReason;
    }
}
