package com.routeshare.controller;

import com.routeshare.model.RideRequest;
import com.routeshare.service.BookingLifecycleService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/bookings"})
public class BookingController {
    private final BookingLifecycleService bookingLifecycleService;

    @Autowired
    public BookingController(BookingLifecycleService bookingLifecycleService) {
        this.bookingLifecycleService = bookingLifecycleService;
    }

    @PostMapping(value={"/{id}/confirm"})
    public ResponseEntity<?> confirm(@PathVariable Long id, @RequestParam Long actorId) {
        return this.executeTransition(() -> this.bookingLifecycleService.confirm(id, actorId));
    }

    @PostMapping(value={"/{id}/reject"})
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestParam Long actorId) {
        return this.executeTransition(() -> this.bookingLifecycleService.reject(id, actorId));
    }

    @PostMapping(value={"/{id}/cancel"})
    public ResponseEntity<?> cancel(@PathVariable Long id, @RequestParam Long actorId) {
        return this.executeTransition(() -> this.bookingLifecycleService.cancel(id, actorId));
    }

    @GetMapping(value={"/rateable/{userId}"})
    public ResponseEntity<?> rateableCounterparts(@PathVariable Long userId) {
        return ResponseEntity.ok(this.bookingLifecycleService.rateableCounterparts(userId));
    }

    @PostMapping(value={"/trip/{tripOfferId}/complete"})
    public ResponseEntity<?> completeTrip(@PathVariable Long tripOfferId, @RequestParam Long actorId) {
        try {
            int completed = this.bookingLifecycleService.completeTrip(tripOfferId, actorId);
            return ResponseEntity.ok((Object)Map.of((Object)"tripOfferId", (Object)tripOfferId, (Object)"completedBookings", (Object)completed));
        }
        catch (SecurityException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.FORBIDDEN).body((Object)Map.of((Object)"error", (Object)e.getMessage()));
        }
        catch (RuntimeException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.NOT_FOUND).body((Object)Map.of((Object)"error", (Object)e.getMessage()));
        }
    }

    private ResponseEntity<?> executeTransition(TransitionAction action) {
        try {
            RideRequest updated = action.run();
            return ResponseEntity.ok((Object)Map.of((Object)"id", (Object)updated.getId(), (Object)"status", (Object)updated.getStatus().toString()));
        }
        catch (SecurityException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.FORBIDDEN).body((Object)Map.of((Object)"error", (Object)e.getMessage()));
        }
        catch (IllegalStateException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.CONFLICT).body((Object)Map.of((Object)"error", (Object)e.getMessage()));
        }
        catch (RuntimeException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.NOT_FOUND).body((Object)Map.of((Object)"error", (Object)e.getMessage()));
        }
    }

    @FunctionalInterface
    private static interface TransitionAction {
        public RideRequest run();
    }
}
