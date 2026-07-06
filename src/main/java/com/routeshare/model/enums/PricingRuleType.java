package com.routeshare.model.enums;

/**
 * PricingRuleType — the vocabulary of the driver-composable pricing policy (FR-16).
 *
 * A driver assembles an ordered set of these rules with her own values; the
 * PricingEngine interprets them per booking. When a driver defines no rules,
 * the platform's default policy chain applies.
 */
public enum PricingRuleType {
    /** Replaces the default €0.50/km base rate with the driver's own rate (value = €/km). */
    BASE_RATE_PER_KM,

    /** Percentage surcharge on weekday rush hours 07–09 and 17–19 (value = %). */
    RUSH_HOUR_SURCHARGE_PCT,

    /** Flat fee added between 00:00 and 05:00 (value = €). */
    LATE_NIGHT_FEE_EUR,

    /** Percentage discount when passenger and driver share the destination (value = %). */
    SAME_DESTINATION_DISCOUNT_PCT,

    /** Incentive mechanism: percentage discount for GOLD or PREMIUM_PRICING
     *  tier passengers — reputation earns cheaper rides (value = %). */
    LOYALTY_TIER_DISCOUNT_PCT
}
