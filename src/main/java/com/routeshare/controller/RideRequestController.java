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
    public RideRequest createRide(@RequestBody RideRequest rideRequest) {
        return rideRequestService.save(rideRequest);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RideRequest> updateRide(@PathVariable Long id, @RequestBody RideRequest rideRequestDetails) {
        try {
            return ResponseEntity.ok(rideRequestService.update(id, rideRequestDetails));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRide(@PathVariable Long id) {
        rideRequestService.delete(id);
        return ResponseEntity.ok().build();
    }
}
