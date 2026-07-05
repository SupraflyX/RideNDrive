package com.routeshare.controller;

import com.routeshare.model.RideRequest;
import com.routeshare.service.BookingLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * BookingController exposes the booking lifecycle state machine over REST.
 *
 * Endpoints (FR-BOOKING lifecycle):
 *   POST /api/bookings/{id}/confirm   driver accepts a pending booking
 *   POST /api/bookings/{id}/reject    driver declines a pending booking
 *   POST /api/bookings/{id}/cancel    passenger withdraws a booking
 *   POST /api/bookings/trip/{tripOfferId}/complete   driver closes a trip
 *
 * Illegal transitions surface as HTTP 409 CONFLICT with a descriptive message,
 * making the state machine observable and testable from the API boundary.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingLifecycleService bookingLifecycleService;

    @Autowired
    public BookingController(BookingLifecycleService bookingLifecycleService) {
        this.bookingLifecycleService = bookingLifecycleService;
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirm(@PathVariable Long id) {
        return executeTransition(() -> bookingLifecycleService.confirm(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id) {
        return executeTransition(() -> bookingLifecycleService.reject(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        return executeTransition(() -> bookingLifecycleService.cancel(id));
    }

    @PostMapping("/trip/{tripOfferId}/complete")
    public ResponseEntity<?> completeTrip(@PathVariable Long tripOfferId) {
        try {
            int completed = bookingLifecycleService.completeTrip(tripOfferId);
            return ResponseEntity.ok(Map.of(
                    "tripOfferId", tripOfferId,
                    "completedBookings", completed
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /** Shared happy-path/error handling for single-booking transitions. */
    private ResponseEntity<?> executeTransition(TransitionAction action) {
        try {
            RideRequest updated = action.run();
            return ResponseEntity.ok(Map.of(
                    "id", updated.getId(),
                    "status", updated.getStatus().toString()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @FunctionalInterface
    private interface TransitionAction {
        RideRequest run();
    }
}
