package com.routeshare.controller;

import com.routeshare.model.TripOffer;
import com.routeshare.model.RideRequest;
import com.routeshare.model.Vehicle;
import com.routeshare.model.dto.StopSequenceResult;
import com.routeshare.service.TripOfferService;
import com.routeshare.service.StopPlanningService;
import com.routeshare.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * TripOfferController exposes REST endpoints for managing TripOffers.
 *
 * Demonstrates:
 * - MVC Architectural Pattern: Receives trip offer creation and retrieval requests.
 */
@RestController
@RequestMapping("/api/trips")
public class TripOfferController {

    private final TripOfferService tripOfferService;
    private final StopPlanningService stopPlanningService;
    private final VehicleService vehicleService;

    @Autowired
    public TripOfferController(TripOfferService tripOfferService, StopPlanningService stopPlanningService, VehicleService vehicleService) {
        this.tripOfferService = tripOfferService;
        this.stopPlanningService = stopPlanningService;
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<TripOffer> getAllTrips() {
        return tripOfferService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripOffer> getTripById(@PathVariable Long id) {
        return tripOfferService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public TripOffer createTrip(@RequestBody TripOffer tripOffer) {
        return tripOfferService.save(tripOffer);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTrip(@PathVariable Long id, @RequestBody TripOffer tripOfferDetails) {
        try {
            TripOffer existingOffer = tripOfferService.findById(id).orElse(null);
            if (existingOffer == null) {
                return ResponseEntity.notFound().build();
            }

            // If there are bound passengers, validate that the edit doesn't break their routing
            List<RideRequest> boundPassengers = existingOffer.getPassengers();
            if (boundPassengers != null && !boundPassengers.isEmpty()) {
                List<Vehicle> vehicles = vehicleService.findByDriverId(existingOffer.getDriver().getId());
                if (vehicles.isEmpty()) {
                    return ResponseEntity.badRequest().body("Driver has no vehicle registered.");
                }
                Vehicle vehicle = vehicles.get(0);

                // Create a temporary offer representing the proposed changes
                TripOffer proposedOffer = new TripOffer(
                        existingOffer.getDriver(),
                        tripOfferDetails.getOrigin(),
                        tripOfferDetails.getDestination(),
                        tripOfferDetails.getDepartureTime(),
                        tripOfferDetails.getMaxStops(),
                        tripOfferDetails.getMaxDetourMinutes()
                );

                StopSequenceResult routingResult = stopPlanningService.planRoute(proposedOffer, boundPassengers, vehicle);
                if (!routingResult.isFeasible()) {
                    return ResponseEntity.badRequest().body("Cannot edit trip: the proposed changes make it impossible to serve your currently booked passengers.");
                }
            }

            return ResponseEntity.ok(tripOfferService.update(id, tripOfferDetails));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrip(@PathVariable Long id) {
        tripOfferService.delete(id);
        return ResponseEntity.ok().build();
    }
}
