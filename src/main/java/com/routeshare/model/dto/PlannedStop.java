package com.routeshare.model.dto;

/* one stop on a planned route. keeping the structured fields next to the label means it
   gets formatted once here instead of callers picking the label apart with substring maths */
public class PlannedStop {

    // ORIGIN and DESTINATION are the driver's own two ends, the others belong to a passenger
    public enum Kind { ORIGIN, PICKUP, DROPOFF, DESTINATION }

    private Kind kind;
    private String location;
    private String passengerName; // null on the driver's own origin/destination
    private String label;

    public PlannedStop() {
    }

    public PlannedStop(Kind kind, String location, String passengerName) {
        this.kind = kind;
        this.location = location;
        this.passengerName = passengerName;
        this.label = format(kind, location, passengerName);
    }

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
