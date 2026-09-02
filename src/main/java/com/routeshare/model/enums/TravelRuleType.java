package com.routeshare.model.enums;

/* the rules a driver can use to filter who rides with them.
   each one is a check against a candidate request */
public enum TravelRuleType {
    MIN_PASSENGER_REPUTATION, // passenger score must be >= the driver's number
    SAME_DESTINATION_ONLY,    // passenger must be going to the trip destination
    NO_LARGE_LUGGAGE          // no LARGE bags
}
