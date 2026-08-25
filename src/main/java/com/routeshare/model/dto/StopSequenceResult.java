package com.routeshare.model.dto;

import java.util.List;
import java.util.Map;

// what the planner gives back: the route, how long it takes, and whether it worked at all
public class StopSequenceResult {

    private List<String> sequence; // display labels, derived from stops
    private List<PlannedStop> stops;

    private int totalTimeMinutes;
    private double totalDistanceKm;
    private boolean feasible;
    private String violationReason;

    // how hard the search worked: nodes visited and how many branches each constraint cut
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

    // setting the stops also rebuilds the label list, so the two can never disagree
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
