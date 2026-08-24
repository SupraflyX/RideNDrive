package com.routeshare.model.enums;

// the states a booking can be in. allowed moves:
//   PENDING   -> CONFIRMED | REJECTED | CANCELLED
//   CONFIRMED -> CANCELLED | COMPLETED
//   REJECTED / CANCELLED / COMPLETED are final
// BookingLifecycleService is what actually enforces this
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    COMPLETED
}
