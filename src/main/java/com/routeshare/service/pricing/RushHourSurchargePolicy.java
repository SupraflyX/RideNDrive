package com.routeshare.service.pricing;

import org.springframework.stereotype.Component;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * RushHourSurchargePolicy implements a 25% price surcharge during commuter peak hours.
 *
 * Demonstrates:
 * - Strategy Pattern implementation: Intercepts and adjusts the cumulative fare based on temporal constraints.
 * - Weekday Peak checks (Rigor & Formality): Evaluates day-of-week and time-range boundaries.
 */
@Component
public class RushHourSurchargePolicy implements PricingPolicy {

    private static final LocalTime MORNING_START = LocalTime.of(7, 0);
    private static final LocalTime MORNING_END = LocalTime.of(9, 0);
    private static final LocalTime EVENING_START = LocalTime.of(17, 0);
    private static final LocalTime EVENING_END = LocalTime.of(19, 0);

    @Override
    public boolean appliesTo(RideContext context) {
        if (context == null || context.getDepartureTime() == null) {
            return false;
        }
        DayOfWeek day = context.getDepartureTime().getDayOfWeek();
        boolean isWeekday = day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
        if (!isWeekday) {
            return false;
        }

        LocalTime time = context.getDepartureTime().toLocalTime();
        boolean isMorningRush = !time.isBefore(MORNING_START) && !time.isAfter(MORNING_END);
        boolean isEveningRush = !time.isBefore(EVENING_START) && !time.isAfter(EVENING_END);

        return isMorningRush || isEveningRush;
    }

    @Override
    public double applyPolicy(double currentFare, RideContext context) {
        // Apply +25% surcharge
        return currentFare * 1.25;
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
