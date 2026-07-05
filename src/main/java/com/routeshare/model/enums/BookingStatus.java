package com.routeshare.model.enums;

/**
 * BookingStatus models the lifecycle of a RideRequest as an explicit finite state machine.
 *
 * Allowed transitions (enforced by BookingLifecycleService):
 *   PENDING   -> CONFIRMED | REJECTED | CANCELLED
 *   CONFIRMED -> CANCELLED | COMPLETED
 *   REJECTED  -> (terminal)
 *   CANCELLED -> (terminal)
 *   COMPLETED -> (terminal)
 *
 * Demonstrates:
 * - State Pattern (GoF Behavioral): Behaviour of a booking depends on its current state.
 * - Rigor & Formality (SE Principle 1): The legal transition relation is defined formally.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    COMPLETED
}
