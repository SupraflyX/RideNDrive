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
 * Endpoints (FR-12 lifecycle, actor-authorized since Sprint 9):
 *   POST /api/bookings/{id}/confirm?actorId=   driver accepts a pending booking
 *   POST /api/bookings/{id}/reject?actorId=    driver declines a pending booking
 *   POST /api/bookings/{id}/cancel?actorId=    passenger withdraws a booking
 *   POST /api/bookings/trip/{tripOfferId}/complete?actorId=   driver closes a trip
 *   GET  /api/bookings/rateable/{userId}       rating eligibility (FR-10)
 *
 * Illegal transitions surface as HTTP 409 CONFLICT; unauthorized actors as
 * HTTP 403 FORBIDDEN — making the state machine observable and testable from
 * the API boundary.
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
    public ResponseEntity<?> confirm(@PathVariable Long id, @RequestParam Long actorId) {
        return executeTransition(() -> bookingLifecycleService.confirm(id, actorId));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestParam Long actorId) {
        return executeTransition(() -> bookingLifecycleService.reject(id, actorId));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, @RequestParam Long actorId) {
        return executeTransition(() -> bookingLifecycleService.cancel(id, actorId));
    }

    /**
     * FR-10 integrity: the users this person may rate — counterparts from
     * COMPLETED bookings only.
     */
    @GetMapping("/rateable/{userId}")
    public ResponseEntity<?> rateableCounterparts(@PathVariable Long userId) {
        return ResponseEntity.ok(bookingLifecycleService.rateableCounterparts(userId));
    }

    @PostMapping("/trip/{tripOfferId}/complete")
    public ResponseEntity<?> completeTrip(@PathVariable Long tripOfferId, @RequestParam Long actorId) {
        try {
            int completed = bookingLifecycleService.completeTrip(tripOfferId, actorId);
            return ResponseEntity.ok(Map.of(
                    "tripOfferId", tripOfferId,
                    "completedBookings", completed
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
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
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
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
