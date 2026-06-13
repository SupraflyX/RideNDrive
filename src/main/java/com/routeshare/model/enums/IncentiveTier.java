package com.routeshare.model.enums;

/**
 * IncentiveTier represents the tier levels mapping from a user's reputation score.
 *
 * Demonstrates:
 * - State Diagram Representation (UML modeling support / Ch. 7): States that a User can transition between based on rating events.
 * - Quality Attributes - Maintainability (Ch. 2): Enumerating tiers allows compile-time checks and localized policy changes.
 */
public enum IncentiveTier {
    STANDARD,
    SILVER,
    GOLD,
    PREMIUM_PRICING
}
