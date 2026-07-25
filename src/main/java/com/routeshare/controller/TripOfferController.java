package com.routeshare.controller;

import com.routeshare.model.RideRequest;
import com.routeshare.model.TripOffer;
import com.routeshare.model.Vehicle;
import com.routeshare.model.dto.StopSequenceResult;
import com.routeshare.model.enums.BookingStatus;
import com.routeshare.service.StopPlanningService;
import com.routeshare.service.TripOfferService;
import com.routeshare.service.VehicleService;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TripOfferController exposes CRUD over driver trip offers (FR-5). Updates that would
 * break the routing of already-booked passengers are rejected (re-planned via the DFS).
 *
 * Note: recovered from bytecode after a disk failure (see docs/RECOVERY_NOTES.md).
 */
@RestController
@RequestMapping("/api/trips")
public class TripOfferController {


    private final TripOfferService tripOfferService;
    private final StopPlanningService stopPlanningService;
    private final VehicleService vehicleService;

    @Autowired
    public TripOfferController(TripOfferService tripOfferService,
                               StopPlanningService stopPlanningService,
                               VehicleService vehicleService) {
        this.tripOfferService = tripOfferService;
        this.stopPlanningService = stopPlanningService;
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<TripOffer> getAllTrips() {
        return this.tripOfferService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripOffer> getTripById(@PathVariable Long id) {
        return this.tripOfferService.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public TripOffer createTrip(@RequestBody TripOffer tripOffer) {
        return this.tripOfferService.save(tripOffer);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTrip(@PathVariable Long id,
                                        @RequestParam Long actorId,
                                        @RequestBody TripOffer tripOfferDetails) {
        TripOffer existingOffer = this.tripOfferService.findById(id).orElse(null);
        if (existingOffer == null) {
            return ResponseEntity.notFound().build();
        }
        // Outside the catch below: SecurityException is a RuntimeException, and being
        // swallowed there would report a rejected edit as a 404 instead of a 403.
        Ownership.require(existingOffer.getDriver() == null ? null : existingOffer.getDriver().getId(),
                actorId, "trip");

        try {
            if (hasCompletedBookings(existingOffer)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "This trip is completed; historical records cannot be modified."));
            }
            List<RideRequest> boundPassengers = existingOffer.getPassengers();
            if (boundPassengers != null && !boundPassengers.isEmpty()) {
                List<Vehicle> vehicles = this.vehicleService.findByDriverId(existingOffer.getDriver().getId());
                if (vehicles.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Driver has no vehicle registered."));
                }
                TripOffer proposedOffer = new TripOffer(existingOffer.getDriver(),
                        tripOfferDetails.getOrigin(), tripOfferDetails.getDestination(),
                        tripOfferDetails.getDepartureTime(), tripOfferDetails.getMaxStops(),
                        tripOfferDetails.getMaxDetourMinutes());
                StopSequenceResult routingResult =
                        this.stopPlanningService.planRoute(proposedOffer, boundPassengers, vehicles.get(0));
                if (!routingResult.isFeasible()) {
                    return ResponseEntity.badRequest().body(Map.of("error",
                            "Cannot edit trip: the proposed changes make it impossible to serve your currently booked passengers."));
                }
            }
            return ResponseEntity.ok(this.tripOfferService.update(id, tripOfferDetails));
        }
        catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTrip(@PathVariable Long id, @RequestParam Long actorId) {
        TripOffer offer = this.tripOfferService.findById(id).orElse(null);
        if (offer == null) {
            return ResponseEntity.notFound().build();
        }
        Ownership.require(offer.getDriver() == null ? null : offer.getDriver().getId(), actorId, "trip");
        if (hasCompletedBookings(offer)) {
            // Deleting would cascade over COMPLETED bookings and orphan the payment
            // ledger references and rating eligibility — completed trips are history.
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "This trip is completed; it cannot be cancelled."));
        }
        this.tripOfferService.delete(id);
        return ResponseEntity.ok().build();
    }

    /** A trip with at least one COMPLETED booking is an immutable historical record. */
    private boolean hasCompletedBookings(TripOffer offer) {
        return offer.getPassengers() != null && offer.getPassengers().stream()
                .anyMatch(r -> r.getStatus() == BookingStatus.COMPLETED);
    }
}
