package com.routeshare.service.pricing;

import com.routeshare.model.enums.IncentiveTier;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * RideContext encapsulates all context metadata needed by pricing policies during rule execution.
 *
 * It also answers the temporal and spatial questions the pricing rules ask ("is this a rush
 * hour?", "same destination zone?"). Both pricing paths — the platform's PricingPolicy chain
 * and the driver-composed rule set (FR-16) — consult these single definitions, so the two can
 * never disagree about when a surcharge applies.
 *
 * Demonstrates:
 * - Parameter Parameterization: Bundling diverse context factors (spatial, temporal, reputational) to pass through the Chain of Responsibility.
 * - Encapsulation: Fields are kept read-only or mutable only via structured methods.
 * - Single Point of Definition (DRY): each pricing condition is expressed exactly once.
 */
public class RideContext {

    private static final LocalTime MORNING_RUSH_START = LocalTime.of(7, 0);
    private static final LocalTime MORNING_RUSH_END = LocalTime.of(9, 0);
    private static final LocalTime EVENING_RUSH_START = LocalTime.of(17, 0);
    private static final LocalTime EVENING_RUSH_END = LocalTime.of(19, 0);
    private static final LocalTime NIGHT_START = LocalTime.of(23, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(5, 0);

    private final LocalDateTime departureTime;
    private final String passengerOrigin;
    private final String passengerDestination;
    private final String driverOrigin;
    private final String driverDestination;
    private final double passengerReputation;
    private final IncentiveTier passengerTier;
    private final double distanceKm;

    public RideContext(LocalDateTime departureTime, String passengerOrigin, String passengerDestination,
                       String driverOrigin, String driverDestination, double passengerReputation,
                       IncentiveTier passengerTier, double distanceKm) {
        this.departureTime = departureTime;
        this.passengerOrigin = passengerOrigin;
        this.passengerDestination = passengerDestination;
        this.driverOrigin = driverOrigin;
        this.driverDestination = driverDestination;
        this.passengerReputation = passengerReputation;
        this.passengerTier = passengerTier;
        this.distanceKm = distanceKm;
    }

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    public String getPassengerOrigin() {
        return passengerOrigin;
    }

    public String getPassengerDestination() {
        return passengerDestination;
    }

    public String getDriverOrigin() {
        return driverOrigin;
    }

    public String getDriverDestination() {
        return driverDestination;
    }

    public double getPassengerReputation() {
        return passengerReputation;
    }

    public IncentiveTier getPassengerTier() {
        return passengerTier;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    // ── pricing conditions (single point of definition) ──────────────

    /** Weekday commuter peak: 07:00–09:00 or 17:00–19:00, Monday to Friday. */
    public boolean isRushHour() {
        if (departureTime == null) {
            return false;
        }
        DayOfWeek day = departureTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = departureTime.toLocalTime();
        return within(time, MORNING_RUSH_START, MORNING_RUSH_END)
                || within(time, EVENING_RUSH_START, EVENING_RUSH_END);
    }

    /** Late night: from 23:00 through 05:00 — an interval that spans midnight. */
    public boolean isLateNight() {
        if (departureTime == null) {
            return false;
        }
        LocalTime time = departureTime.toLocalTime();
        return !time.isBefore(NIGHT_START) || !time.isAfter(NIGHT_END);
    }

    /** True when the passenger is heading to the driver's own destination. */
    public boolean isSameDestinationZone() {
        return passengerDestination != null && driverDestination != null
                && passengerDestination.trim().equalsIgnoreCase(driverDestination.trim());
    }

    /** Incentive mechanism: the tiers that a loyalty discount rewards. */
    public boolean isLoyaltyTier() {
        return passengerTier == IncentiveTier.GOLD || passengerTier == IncentiveTier.PREMIUM_PRICING;
    }

    /** Inclusive on both ends, matching the published surcharge windows. */
    private static boolean within(LocalTime time, LocalTime start, LocalTime end) {
        return !time.isBefore(start) && !time.isAfter(end);
    }
}
