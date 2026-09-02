package com.routeshare.model.enums;

/* the states a booking can be in:
     PENDING   -> CONFIRMED | REJECTED | CANCELLED
     CONFIRMED -> CANCELLED | COMPLETED
     REJECTED / CANCELLED / COMPLETED are final. BookingLifecycleService enforces this */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    COMPLETED
}
