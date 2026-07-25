package com.routeshare.model.dto;

/**
 * PlannedStop is one stop of a planned route, in structured form.
 *
 * The planner has always produced human-readable labels ("PICKUP(Alice) at Messina").
 * Every consumer — the passenger-metrics walk, the driver cockpit's waypoints, and
 * three separate places in the SPA — used to parse those labels back into data with
 * its own substring arithmetic. Carrying the structure alongside the label means the
 * label is formatted once, here, and never parsed again.
 *
 * Demonstrates:
 * - Data Transfer Object Pattern: the API speaks data, not display strings.
 * - Single Point of Definition (DRY): one formatter, zero parsers.
 */
public class PlannedStop {

    /** ORIGIN and DESTINATION are the driver's own endpoints; PICKUP/DROPOFF belong to a passenger. */
    public enum Kind { ORIGIN, PICKUP, DROPOFF, DESTINATION }

    private Kind kind;
    private String location;
    /** Null for the driver's own ORIGIN and DESTINATION stops. */
    private String passengerName;
    /** The display label, kept so existing clients and tests read unchanged. */
    private String label;

    public PlannedStop() {
    }

    public PlannedStop(Kind kind, String location, String passengerName) {
        this.kind = kind;
        this.location = location;
        this.passengerName = passengerName;
        this.label = format(kind, location, passengerName);
    }

    /** The one place a stop label is composed. */
    public static String format(Kind kind, String location, String passengerName) {
        return switch (kind) {
            case ORIGIN -> "Origin: " + location;
            case DESTINATION -> "Destination: " + location;
            default -> kind + "(" + passengerName + ") at " + location;
        };
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public void setPassengerName(String passengerName) {
        this.passengerName = passengerName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
