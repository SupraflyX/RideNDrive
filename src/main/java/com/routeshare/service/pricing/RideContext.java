package com.routeshare.service.pricing;

import com.routeshare.model.enums.IncentiveTier;
import java.time.LocalDateTime;

/**
 * RideContext encapsulates all context metadata needed by pricing policies during rule execution.
 *
 * Demonstrates:
 * - Parameter Parameterization: Bundling diverse context factors (spatial, temporal, reputational) to pass through the Chain of Responsibility.
 * - Encapsulation: Fields are kept read-only or mutable only via structured methods.
 */
public class RideContext {

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
}
