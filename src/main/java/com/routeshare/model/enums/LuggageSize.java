package com.routeshare.model.enums;

/**
 * LuggageSize declared on a ride request — evaluated by driver travel rules
 * (NO_LARGE_LUGGAGE) and used as a tie-breaker in candidate ranking.
 */
public enum LuggageSize {
    NONE,
    SMALL,
    LARGE
}
