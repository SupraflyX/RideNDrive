package com.routeshare.service.integration;

// distances and travel times. keeps the planner from caring who actually supplies them
public interface MappingService {
    double getDistanceKm(String origin, String destination);
    int getTravelTimeMinutes(String origin, String destination);
}
