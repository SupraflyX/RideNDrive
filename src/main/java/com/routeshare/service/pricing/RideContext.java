package com.routeshare.service.pricing;

import com.routeshare.model.enums.IncentiveTier;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/* everything a pricing rule might need to look at, plus the questions they ask
   ("is this rush hour?", "same destination?"). both pricing paths use these same
   definitions so they can't drift apart */
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

    // the conditions the rules check

    // 07:00-09:00 or 17:00-19:00, monday to friday
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

    // 23:00 through to 05:00, so it wraps past midnight
    public boolean isLateNight() {
        if (departureTime == null) {
            return false;
        }
        LocalTime time = departureTime.toLocalTime();
        return !time.isBefore(NIGHT_START) || !time.isAfter(NIGHT_END);
    }

    public boolean isSameDestinationZone() {
        return passengerDestination != null && driverDestination != null
                && passengerDestination.trim().equalsIgnoreCase(driverDestination.trim());
    }

    // which tiers get the loyalty discount
    public boolean isLoyaltyTier() {
        return passengerTier == IncentiveTier.GOLD || passengerTier == IncentiveTier.PREMIUM_PRICING;
    }

    // both ends count as inside the window
    private static boolean within(LocalTime time, LocalTime start, LocalTime end) {
        return !time.isBefore(start) && !time.isAfter(end);
    }
}
