package com.routeshare.model.enums;

/**
 * UserRole defines the functional role of a user in the commuter carpooling system.
 *
 * Demonstrates:
 * - Separation of Concerns (SE Principle 2): Separating user capabilities based on roles.
 * - Modularity (SE Principle 3): Rigid enum typing for clean compile-time validation.
 */
public enum UserRole {
    DRIVER,
    PASSENGER
}
