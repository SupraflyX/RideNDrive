package com.routeshare.model.dto;

import java.util.List;
import java.util.Map;

/**
 * StopSequenceResult is a Data Transfer Object (DTO) returning the results of the Stop Planning Algorithm.
 *
 * Demonstrates:
 * - Data Transfer Object Pattern: Decoupling algorithmic execution results from entity models.
 * - Separation of Concerns: Capturing routing details and constraint feasibility metadata (e.g. violation reason).
 */
public class StopSequenceResult {

    private List<String> sequence;

    /**
     * The same route in structured form (kind / location / passenger), so consumers
     * never parse the display labels in {@link #sequence} back into data.
     */
    private List<PlannedStop> stops;

    private int totalTimeMinutes;
    private double totalDistanceKm;
    private boolean feasible;
    private String violationReason;

    /** Search instrumentation (NFR transparency): nodes explored and prunings per constraint. */
    private Map<String, Object> searchStats;

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

    public List<PlannedStop> getStops() {
        return stops;
    }

    /** Sets the structured stops and derives the display sequence from them. */
    public void setStops(List<PlannedStop> stops) {
        this.stops = stops;
        this.sequence = stops == null ? null : stops.stream().map(PlannedStop::getLabel).toList();
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

    public Map<String, Object> getSearchStats() {
        return searchStats;
    }

    public void setSearchStats(Map<String, Object> searchStats) {
        this.searchStats = searchStats;
    }

    public void setViolationReason(String violationReason) {
        this.violationReason = violationReason;
    }
}
