package com.routeshare.controller;

import com.routeshare.model.RideRequest;
import com.routeshare.service.RideRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * RideRequestController exposes REST endpoints for managing RideRequests.
 *
 * Demonstrates:
 * - MVC Architectural Pattern: Receives ride request creation and retrieval requests.
 */
@RestController
@RequestMapping("/api/rides")
public class RideRequestController {

    private final RideRequestService rideRequestService;

    @Autowired
    public RideRequestController(RideRequestService rideRequestService) {
        this.rideRequestService = rideRequestService;
    }

    @GetMapping
    public List<RideRequest> getAllRides() {
        return rideRequestService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RideRequest> getRideById(@PathVariable Long id) {
        return rideRequestService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createRide(@RequestBody RideRequest rideRequest) {
        // Creation-time temporal rule (moved off the entity so lifecycle updates
        // of historical bookings are not re-validated at flush):
        if (rideRequest.getPickupTimeWindowEnd() != null
                && rideRequest.getPickupTimeWindowEnd().isBefore(java.time.LocalDateTime.now())) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", "The pickup time window has already passed."));
        }
        return ResponseEntity.ok(rideRequestService.save(rideRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RideRequest> updateRide(@PathVariable Long id,
                                                  @RequestParam Long actorId,
                                                  @RequestBody RideRequest rideRequestDetails) {
        RideRequest existing = rideRequestService.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        Ownership.require(existing.getPassenger() == null ? null : existing.getPassenger().getId(),
                actorId, "booking");
        return ResponseEntity.ok(rideRequestService.update(id, rideRequestDetails));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRide(@PathVariable Long id, @RequestParam Long actorId) {
        RideRequest existing = rideRequestService.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.ok().build(); // deleting a non-existent booking is a no-op
        }
        Ownership.require(existing.getPassenger() == null ? null : existing.getPassenger().getId(),
                actorId, "booking");
        rideRequestService.delete(id);
        return ResponseEntity.ok().build();
    }
}
