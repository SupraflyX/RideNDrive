package com.routeshare.model.enums;

/**
 * TravelRuleType — the vocabulary of the driver-defined travel policy engine (FR-15).
 *
 * Each type is a predicate evaluated against a candidate passenger/request.
 * Rules are composed per driver (Specification pattern): the driver owns the
 * policy, the platform only interprets it — this is rule/policy customization,
 * not parameter tuning.
 */
public enum TravelRuleType {
    /** Candidate's reputation score must be >= numericValue (e.g. 4.0). */
    MIN_PASSENGER_REPUTATION,

    /** Candidate's destination must match the trip destination (same zone). */
    SAME_DESTINATION_ONLY,

    /** Candidate must not carry LARGE luggage. */
    NO_LARGE_LUGGAGE
}
