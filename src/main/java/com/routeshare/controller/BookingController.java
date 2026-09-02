package com.routeshare.controller;

import com.routeshare.model.RideRequest;
import com.routeshare.service.BookingLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// a move that isn't allowed comes back 409, the wrong person acting comes back 403
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

    // same success and error handling for all three single-booking moves
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
