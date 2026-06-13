package com.routeshare.service.integration;

/**
 * MappingService defines a provides-interface for geographic routing and distance computation.
 *
 * Demonstrates:
 * - Abstraction & Modularity (SE Principle 4, 3): Hiding the routing supplier from the core stop planning logic.
 * - Component-Based Software Engineering (CBSE / Ch. 10): Defining clean boundary interfaces.
 */
public interface MappingService {
    double getDistanceKm(String origin, String destination);
    int getTravelTimeMinutes(String origin, String destination);
}
