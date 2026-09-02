package com.routeshare.model.enums;

/* the pricing rules a driver can build their own policy from.
   PricingEngine reads these per booking. no rules = platform defaults apply */
public enum PricingRuleType {
    BASE_RATE_PER_KM,              // driver's own rate instead of the default 0.50/km. value = eur per km
    RUSH_HOUR_SURCHARGE_PCT,       // weekday 07-09 and 17-19. value = %
    LATE_NIGHT_FEE_EUR,            // flat fee for departures 23:00-05:00. value = eur
    SAME_DESTINATION_DISCOUNT_PCT, // passenger going where the driver is going. value = %
    LOYALTY_TIER_DISCOUNT_PCT      // discount for GOLD/PREMIUM passengers. value = %
}
