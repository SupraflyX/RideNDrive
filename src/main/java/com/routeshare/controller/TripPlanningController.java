package com.routeshare.controller;

import com.routeshare.model.RideRequest;
import com.routeshare.model.TripOffer;
import com.routeshare.model.User;
import com.routeshare.model.Vehicle;
import com.routeshare.model.dto.PricingResult;
import com.routeshare.model.dto.StopSequenceResult;
import com.routeshare.service.*;
import com.routeshare.service.integration.MappingService;
import com.routeshare.service.integration.PaymentService;
import com.routeshare.service.pricing.PricingEngine;
import com.routeshare.service.pricing.RideContext;
import com.routeshare.exception.MapApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TripPlanningController orchestrates the end-to-end carpool workflow connecting database entities,
 * routing services, pricing strategy chains, and payment processing component models.
 */
@RestController
@RequestMapping("/api/trips")
public class TripPlanningController {

    private final TripOfferService tripOfferService;
    private final RideRequestService rideRequestService;
    private final VehicleService vehicleService;
    private final StopPlanningService stopPlanningService;
    private final PricingEngine pricingEngine;
    private final MappingService mappingService;
    private final PaymentService paymentService;
    private final UserService userService;

    @Autowired
    public TripPlanningController(TripOfferService tripOfferService,
                                  RideRequestService rideRequestService,
                                  VehicleService vehicleService,
                                  StopPlanningService stopPlanningService,
                                  PricingEngine pricingEngine,
                                  MappingService mappingService,
                                  PaymentService paymentService,
                                  UserService userService) {
        this.tripOfferService = tripOfferService;
        this.rideRequestService = rideRequestService;
        this.vehicleService = vehicleService;
        this.stopPlanningService = stopPlanningService;
        this.pricingEngine = pricingEngine;
        this.mappingService = mappingService;
        this.paymentService = paymentService;
        this.userService = userService;
    }

    /**
     * Executes route planning, pricing strategies, and mock payment distributions for a given trip offer.
     */
    @PostMapping("/{tripOfferId}/plan")
    public ResponseEntity<?> planAndBookTrip(
            @PathVariable Long tripOfferId,
            @RequestBody List<Long> requestIds) {

        Map<String, Object> response = new HashMap<>();

        try {
            TripOffer offer = tripOfferService.findById(tripOfferId).orElse(null);
            if (offer == null) {
                return ResponseEntity.badRequest().body("TripOffer not found with id: " + tripOfferId);
            }

            User driver = offer.getDriver();
            List<Vehicle> vehicles = vehicleService.findByDriverId(driver.getId());
            if (vehicles.isEmpty()) {
                return ResponseEntity.badRequest().body("Driver has no vehicle registered.");
            }
            Vehicle vehicle = vehicles.get(0);

            List<RideRequest> requests = new ArrayList<>();
            for (Long rId : requestIds) {
                rideRequestService.findById(rId).ifPresent(requests::add);
            }

            StopSequenceResult routingResult = stopPlanningService.planRoute(offer, requests, vehicle);
            response.put("routing", routingResult);

            if (!routingResult.isFeasible()) {
                response.put("bookingStatus", "FAILED_ROUTING");
                return ResponseEntity.ok(response);
            }

            List<Map<String, Object>> passengersBreakdown = new ArrayList<>();
            boolean paymentSuccess = true;

            for (RideRequest req : requests) {
                User passenger = req.getPassenger();
                double passengerDistance = mappingService.getDistanceKm(req.getOrigin(), req.getDestination());

                RideContext pricingContext = new RideContext(
                        offer.getDepartureTime(),
                        req.getOrigin(),
                        req.getDestination(),
                        offer.getOrigin(),
                        offer.getDestination(),
                        passenger.getReputationScore(),
                        passenger.getIncentiveTier(),
                        passengerDistance
                );

                PricingResult pricingResult = pricingEngine.calculateFare(pricingContext);
                boolean identityVerified = paymentService.verifyIdentity(passenger.getId());
                boolean transactionCleared = false;
                if (identityVerified) {
                    transactionCleared = paymentService.processPayment(
                            passenger.getId(),
                            driver.getId(),
                            pricingResult.getFinalFare()
                    );
                }

                if (!identityVerified || !transactionCleared) {
                    paymentSuccess = false;
                }

                Map<String, Object> pDetail = new HashMap<>();
                pDetail.put("passengerId", passenger.getId());
                pDetail.put("passengerName", passenger.getName());
                pDetail.put("reputationScore", passenger.getReputationScore());
                pDetail.put("incentiveTier", passenger.getIncentiveTier());
                pDetail.put("pricing", pricingResult);
                pDetail.put("identityVerified", identityVerified);
                pDetail.put("paymentCleared", transactionCleared);

                passengersBreakdown.add(pDetail);
            }

            response.put("passengers", passengersBreakdown);
            response.put("bookingStatus", paymentSuccess ? "SUCCESSFUL" : "PAYMENT_HOLD");

            return ResponseEntity.ok(response);
        } catch (MapApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Google Maps API Error: " + e.getMessage());
        }
    }

    /**
     * Searches for active TripOffers that can accommodate a passenger's candidate ride request
     * without exceeding the driver's detour budgets, capacity, or passenger's time windows.
     */
    @PostMapping("/search-matches")
    public ResponseEntity<?> searchMatchingTrips(@RequestBody Map<String, String> payload) {
        String origin = payload.get("origin");
        String destination = payload.get("destination");
        String dateStr = payload.get("date");
        String passengerIdStr = payload.get("passengerId");

        if (origin == null || destination == null || passengerIdStr == null || dateStr == null) {
            return ResponseEntity.badRequest().body("Invalid search parameters.");
        }

        Long passengerId = Long.parseLong(passengerIdStr);
        User passenger = userService.findById(passengerId).orElse(null);
        if (passenger == null) {
            return ResponseEntity.badRequest().body("Passenger user not found.");
        }

        java.time.LocalDate searchDate = java.time.LocalDate.parse(dateStr);

        List<TripOffer> allOffers = tripOfferService.findAll();
        List<Map<String, Object>> matchingResults = new ArrayList<>();

        try {
            double passengerDistance = mappingService.getDistanceKm(origin, destination);

            for (TripOffer offer : allOffers) {
                if (!offer.getDepartureTime().toLocalDate().equals(searchDate)) {
                    continue;
                }

                List<Vehicle> vehicles = vehicleService.findByDriverId(offer.getDriver().getId());
                if (vehicles.isEmpty()) {
                    continue;
                }
                Vehicle vehicle = vehicles.get(0);

                // Automatically set time window relative to driver's departure time (e.g. +/- 12 hours)
                java.time.LocalDateTime windowStart = offer.getDepartureTime().minusHours(12);
                java.time.LocalDateTime windowEnd = offer.getDepartureTime().plusHours(12);

                // Create temporary request for routing feasibility check
                RideRequest tempRequest = new RideRequest(passenger, origin, destination, windowStart, windowEnd);
                tempRequest.setId(-1L);
                List<RideRequest> tempRequests = List.of(tempRequest);

                try {
                    StopSequenceResult routingResult = stopPlanningService.planRoute(offer, tempRequests, vehicle);
                    if (routingResult.isFeasible()) {
                        RideContext pricingContext = new RideContext(
                                offer.getDepartureTime(),
                                origin,
                                destination,
                                offer.getOrigin(),
                                offer.getDestination(),
                                passenger.getReputationScore(),
                                passenger.getIncentiveTier(),
                                passengerDistance
                        );
                        PricingResult pricingResult = pricingEngine.calculateFare(pricingContext);

                        Map<String, Object> match = new HashMap<>();
                        match.put("tripOfferId", offer.getId());
                        match.put("driverName", offer.getDriver().getName());
                        match.put("vehicleInfo", vehicle.getMake() + " " + vehicle.getModel());
                        match.put("departureTime", offer.getDepartureTime().toString());
                        match.put("totalTimeMinutes", routingResult.getTotalTimeMinutes());
                        match.put("totalDistanceKm", routingResult.getTotalDistanceKm());
                        match.put("spotsAvailable", vehicle.getCapacity());
                        match.put("routing", routingResult);
                        match.put("pricing", pricingResult);

                        matchingResults.add(match);
                    }
                } catch (MapApiException e) {
                    System.out.println("Skipping incompatible trip offer ID " + offer.getId() + " due to mapping error: " + e.getMessage());
                }
            }
        } catch (MapApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Google Maps API Error: " + e.getMessage());
        }

        return ResponseEntity.ok(matchingResults);
    }

    /**
     * Confirms booking of a trip for a passenger by registering their request, linking it,
     * and running Stripe transaction clearing.
     */
    @PostMapping("/{tripOfferId}/book-passenger")
    public ResponseEntity<?> bookPassengerOnTrip(
            @PathVariable Long tripOfferId,
            @RequestBody Map<String, String> payload) {

        Map<String, Object> response = new HashMap<>();

        String origin = payload.get("origin");
        String destination = payload.get("destination");
        String passengerIdStr = payload.get("passengerId");

        if (origin == null || destination == null || passengerIdStr == null) {
            return ResponseEntity.badRequest().body("Invalid payload parameters.");
        }

        try {
            TripOffer offer = tripOfferService.findById(tripOfferId).orElse(null);
            if (offer == null) {
                return ResponseEntity.badRequest().body("TripOffer not found with id: " + tripOfferId);
            }

            Long passengerId = Long.parseLong(passengerIdStr);
            User passenger = userService.findById(passengerId).orElse(null);
            if (passenger == null) {
                return ResponseEntity.badRequest().body("Passenger user not found with id: " + passengerId);
            }

            // Prevent duplicate booking
            boolean alreadyBooked = offer.getPassengers().stream()
                    .anyMatch(req -> req.getPassenger().getId().equals(passengerId));
            if (alreadyBooked) {
                return ResponseEntity.badRequest().body("You have already booked this trip.");
            }

            String windowStartStr = payload.get("pickupTimeWindowStart");
            String windowEndStr = payload.get("pickupTimeWindowEnd");
            java.time.LocalDateTime windowStart;
            java.time.LocalDateTime windowEnd;

            if (windowStartStr == null || windowEndStr == null) {
                windowStart = offer.getDepartureTime().minusHours(12);
                windowEnd = offer.getDepartureTime().plusHours(12);
            } else {
                windowStart = java.time.LocalDateTime.parse(windowStartStr);
                windowEnd = java.time.LocalDateTime.parse(windowEndStr);
            }

            // 1. Create and persist the actual RideRequest
            RideRequest rideRequest = new RideRequest(passenger, origin, destination, windowStart, windowEnd);
            rideRequest.setTripOffer(offer);
            rideRequest = rideRequestService.save(rideRequest);

            // 2. Fetch driver's vehicle
            User driver = offer.getDriver();
            List<Vehicle> vehicles = vehicleService.findByDriverId(driver.getId());
            if (vehicles.isEmpty()) {
                return ResponseEntity.badRequest().body("Driver has no vehicle registered.");
            }
            Vehicle vehicle = vehicles.get(0);

            // 3. Run Algorithmic Stop Planning Sequence DFS for this booking
            List<RideRequest> requests = List.of(rideRequest);
            StopSequenceResult routingResult = stopPlanningService.planRoute(offer, requests, vehicle);
            response.put("routing", routingResult);

            if (!routingResult.isFeasible()) {
                rideRequestService.delete(rideRequest.getId());
                response.put("bookingStatus", "FAILED_ROUTING");
                return ResponseEntity.ok(response);
            }

            // 4. Calculate direct passenger travel distance
            double passengerDistance = mappingService.getDistanceKm(rideRequest.getOrigin(), rideRequest.getDestination());

            // 5. Build pricing context and calculate fare
            RideContext pricingContext = new RideContext(
                    offer.getDepartureTime(),
                    rideRequest.getOrigin(),
                    rideRequest.getDestination(),
                    offer.getOrigin(),
                    offer.getDestination(),
                    passenger.getReputationScore(),
                    passenger.getIncentiveTier(),
                    passengerDistance
            );
            PricingResult pricingResult = pricingEngine.calculateFare(pricingContext);

            // 6. Process payment transaction
            boolean identityVerified = paymentService.verifyIdentity(passenger.getId());
            boolean transactionCleared = false;
            if (identityVerified) {
                transactionCleared = paymentService.processPayment(
                        passenger.getId(),
                        driver.getId(),
                        pricingResult.getFinalFare()
                );
            }

            List<Map<String, Object>> passengersBreakdown = new ArrayList<>();
            Map<String, Object> pDetail = new HashMap<>();
            pDetail.put("passengerId", passenger.getId());
            pDetail.put("passengerName", passenger.getName());
            pDetail.put("reputationScore", passenger.getReputationScore());
            pDetail.put("incentiveTier", passenger.getIncentiveTier());
            pDetail.put("pricing", pricingResult);
            pDetail.put("identityVerified", identityVerified);
            pDetail.put("paymentCleared", transactionCleared);
            passengersBreakdown.add(pDetail);

            response.put("passengers", passengersBreakdown);
            response.put("bookingStatus", (identityVerified && transactionCleared) ? "SUCCESSFUL" : "PAYMENT_HOLD");

            return ResponseEntity.ok(response);
        } catch (MapApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Google Maps API Error: " + e.getMessage());
        }
    }

    /**
     * Checks if a driver has any existing TripOffers that can accommodate a specific RideRequest.
     */
    @PostMapping("/check-existing-matches")
    public ResponseEntity<?> checkExistingMatches(@RequestBody Map<String, String> payload) {
        String driverIdStr = payload.get("driverId");
        String rideRequestIdStr = payload.get("rideRequestId");

        if (driverIdStr == null || rideRequestIdStr == null) {
            return ResponseEntity.badRequest().body("Invalid parameters.");
        }

        try {
            Long driverId = Long.parseLong(driverIdStr);
            Long rideRequestId = Long.parseLong(rideRequestIdStr);

            RideRequest request = rideRequestService.findById(rideRequestId).orElse(null);
            if (request == null) {
                return ResponseEntity.badRequest().body("RideRequest not found.");
            }

            List<Vehicle> vehicles = vehicleService.findByDriverId(driverId);
            if (vehicles.isEmpty()) {
                return ResponseEntity.badRequest().body("Driver has no vehicle registered.");
            }
            Vehicle vehicle = vehicles.get(0);

            List<TripOffer> driverOffers = tripOfferService.findAll().stream()
                    .filter(o -> o.getDriver().getId().equals(driverId))
                    .toList();

            List<Map<String, Object>> matchingResults = new ArrayList<>();
            List<RideRequest> testRequests = List.of(request);

            for (TripOffer offer : driverOffers) {
                try {
                    StopSequenceResult routingResult = stopPlanningService.planRoute(offer, testRequests, vehicle);
                    if (routingResult.isFeasible()) {
                        Map<String, Object> match = new HashMap<>();
                        match.put("tripOfferId", offer.getId());
                        match.put("origin", offer.getOrigin());
                        match.put("destination", offer.getDestination());
                        match.put("departureTime", offer.getDepartureTime().toString());
                        match.put("totalTimeMinutes", routingResult.getTotalTimeMinutes());
                        match.put("totalDistanceKm", routingResult.getTotalDistanceKm());
                        matchingResults.add(match);
                    }
                } catch (MapApiException e) {
                    System.out.println("Skipping incompatible driver offer ID " + offer.getId() + " due to mapping error: " + e.getMessage());
                }
            }

            return ResponseEntity.ok(matchingResults);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing matches: " + e.getMessage());
        }
    }
}
