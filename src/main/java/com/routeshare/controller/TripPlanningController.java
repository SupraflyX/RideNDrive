package com.routeshare.controller;

import com.routeshare.exception.MapApiException;
import com.routeshare.model.RideRequest;
import com.routeshare.model.TripOffer;
import com.routeshare.model.User;
import com.routeshare.model.Vehicle;
import com.routeshare.model.dto.PricingResult;
import com.routeshare.model.dto.StopSequenceResult;
import com.routeshare.model.enums.BookingStatus;
import com.routeshare.model.enums.LuggageSize;
import com.routeshare.model.enums.NotificationType;
import com.routeshare.repository.DriverPricingRuleRepository;
import com.routeshare.service.NotificationService;
import com.routeshare.service.RideRequestService;
import com.routeshare.service.StopPlanningService;
import com.routeshare.service.TripOfferService;
import com.routeshare.service.UserService;
import com.routeshare.service.VehicleService;
import com.routeshare.service.integration.MappingService;
import com.routeshare.service.integration.PaymentService;
import com.routeshare.service.policy.TravelPolicyService;
import com.routeshare.service.pricing.PricingEngine;
import com.routeshare.service.pricing.RideContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TripPlanningController orchestrates the end-to-end carpool workflow connecting database entities,
 * routing services, driver policies, pricing strategy chains, and payment processing.
 *
 * Note: recovered from bytecode after a disk failure (see RECOVERY_NOTES); the logic is the
 * exact compiled Sprint 9 behaviour, decompiler-style casts included.
 */
@RestController
@RequestMapping(value={"/api/trips"})
public class TripPlanningController {
    private final TripOfferService tripOfferService;
    private final RideRequestService rideRequestService;
    private final VehicleService vehicleService;
    private final StopPlanningService stopPlanningService;
    private final PricingEngine pricingEngine;
    private final MappingService mappingService;
    private final PaymentService paymentService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final TravelPolicyService travelPolicyService;
    private final DriverPricingRuleRepository driverPricingRuleRepository;
    private final com.routeshare.service.PaymentLedgerService paymentLedgerService;

    @Autowired
    public TripPlanningController(TripOfferService tripOfferService, RideRequestService rideRequestService, VehicleService vehicleService, StopPlanningService stopPlanningService, PricingEngine pricingEngine, MappingService mappingService, PaymentService paymentService, UserService userService, NotificationService notificationService, TravelPolicyService travelPolicyService, DriverPricingRuleRepository driverPricingRuleRepository, com.routeshare.service.PaymentLedgerService paymentLedgerService) {
        this.tripOfferService = tripOfferService;
        this.rideRequestService = rideRequestService;
        this.vehicleService = vehicleService;
        this.stopPlanningService = stopPlanningService;
        this.pricingEngine = pricingEngine;
        this.mappingService = mappingService;
        this.paymentService = paymentService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.travelPolicyService = travelPolicyService;
        this.driverPricingRuleRepository = driverPricingRuleRepository;
        this.paymentLedgerService = paymentLedgerService;
    }

    /**
     * Passenger-perspective metrics (FR-14): the searcher cares about THEIR leg,
     * not the driver's whole trip. Walks the planned sequence from the searcher's
     * PICKUP to their DROPOFF, summing leg times (intermediate stops included).
     */
    private Map<String, Object> yourRideMetrics(List<String> sequence, String passengerName,
                                                double directDistanceKm) {
        HashMap m = new HashMap();
        m.put("distanceKm", (Math.round(directDistanceKm * 10.0) / 10.0));
        if (sequence == null) return m;
        java.util.List<String> locs = new ArrayList<>();
        int pickupIdx = -1, dropoffIdx = -1;
        for (String label : sequence) {
            int at = label.lastIndexOf(" at ");
            int colon = label.indexOf(": ");
            String loc = at >= 0 ? label.substring(at + 4) : (colon >= 0 ? label.substring(colon + 2) : label);
            locs.add(loc);
            if (label.startsWith("PICKUP(" + passengerName + ")")) pickupIdx = locs.size() - 1;
            if (label.startsWith("DROPOFF(" + passengerName + ")")) dropoffIdx = locs.size() - 1;
        }
        if (pickupIdx >= 0 && dropoffIdx > pickupIdx) {
            int inCar = 0;
            for (int i = pickupIdx; i < dropoffIdx; i++) {
                inCar += this.mappingService.getTravelTimeMinutes(locs.get(i), locs.get(i + 1));
            }
            m.put("inCarTimeMinutes", inCar);
            m.put("stopsBeforeDropoff", Math.max(0, dropoffIdx - pickupIdx - 1));
        }
        return m;
    }

    /** Active bookings (PENDING or CONFIRMED) currently attached to an offer. */
    private static List<RideRequest> activeBookings(TripOffer offer) {
        ArrayList active = new ArrayList();
        if (offer.getPassengers() != null) {
            for (RideRequest r : offer.getPassengers()) {
                BookingStatus s;
                BookingStatus bookingStatus = s = r.getStatus() == null ? BookingStatus.PENDING : r.getStatus();
                if (s != BookingStatus.PENDING && s != BookingStatus.CONFIRMED) continue;
                active.add(r);
            }
        }
        return active;
    }

    /** Executes route planning, pricing and mock payment distribution for a given trip offer. */
    @PostMapping(value={"/{tripOfferId}/plan"})
    public ResponseEntity<?> planAndBookTrip(@PathVariable Long tripOfferId, @RequestBody List<Long> requestIds) {
        HashMap response = new HashMap();
        try {
            TripOffer offer = (TripOffer)this.tripOfferService.findById(tripOfferId).orElse(null);
            if (offer == null) {
                return ResponseEntity.badRequest().body(("TripOffer not found with id: " + tripOfferId));
            }
            User driver = offer.getDriver();
            List<Vehicle> vehicles = this.vehicleService.findByDriverId(driver.getId());
            if (vehicles.isEmpty()) {
                return ResponseEntity.badRequest().body("Driver has no vehicle registered.");
            }
            Vehicle vehicle = (Vehicle)vehicles.get(0);
            ArrayList<RideRequest> requests = new ArrayList<>();
            for (Long rId : requestIds) {
                this.rideRequestService.findById(rId).ifPresent(arg_0 -> (requests).add(arg_0));
            }
            StopSequenceResult routingResult = this.stopPlanningService.planRoute(offer, requests, vehicle);
            response.put("routing", routingResult);
            if (!routingResult.isFeasible()) {
                response.put("bookingStatus", "FAILED_ROUTING");
                return ResponseEntity.ok(response);
            }
            ArrayList passengersBreakdown = new ArrayList();
            boolean paymentSuccess = true;
            for (RideRequest req : requests) {
                User passenger = req.getPassenger();
                double passengerDistance = this.mappingService.getDistanceKm(req.getOrigin(), req.getDestination());
                RideContext pricingContext = new RideContext(offer.getDepartureTime(), req.getOrigin(), req.getDestination(), offer.getOrigin(), offer.getDestination(), passenger.getReputationScore(), passenger.getIncentiveTier(), passengerDistance);
                PricingResult pricingResult = this.pricingEngine.calculateFare(pricingContext);
                boolean identityVerified = this.paymentService.verifyIdentity(passenger.getId());
                boolean transactionCleared = false;
                if (identityVerified) {
                    transactionCleared = this.paymentService.processPayment(passenger.getId(), driver.getId(), pricingResult.getFinalFare());
                }
                if (!identityVerified || !transactionCleared) {
                    paymentSuccess = false;
                }
                HashMap pDetail = new HashMap();
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
            response.put("bookingStatus", (paymentSuccess ? "SUCCESSFUL" : "PAYMENT_HOLD"));
            return ResponseEntity.ok(response);
        }
        catch (MapApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(("Google Maps API Error: " + e.getMessage()));
        }
    }

    /**
     * FR-14: feasibility-aware search - every candidate offer is vetted by the driver's
     * travel policy (FR-15) and planned TOGETHER with its active bookings (overbooking-aware).
     */
    @PostMapping(value={"/search-matches"})
    public ResponseEntity<?> searchMatchingTrips(@RequestBody Map<String, String> payload) {
        String origin = payload.get("origin");
        String destination = payload.get("destination");
        String dateStr = payload.get("date");
        String passengerIdStr = payload.get("passengerId");
        if (origin == null || destination == null || passengerIdStr == null || dateStr == null) {
            return ResponseEntity.badRequest().body("Invalid search parameters.");
        }
        Long passengerId = Long.parseLong(passengerIdStr);
        User passenger = (User)this.userService.findById(passengerId).orElse(null);
        if (passenger == null) {
            return ResponseEntity.badRequest().body("Passenger user not found.");
        }
        LocalDate searchDate = LocalDate.parse(dateStr);
        List<TripOffer> allOffers = this.tripOfferService.findAll();
        ArrayList matchingResults = new ArrayList();
        try {
            double passengerDistance = this.mappingService.getDistanceKm(origin, destination);
            for (TripOffer offer : allOffers) {
                List<Vehicle> vehicles;
                if (!offer.getDepartureTime().toLocalDate().equals(searchDate) || (vehicles = this.vehicleService.findByDriverId(offer.getDriver().getId())).isEmpty()) continue;
                Vehicle vehicle = (Vehicle)vehicles.get(0);
                LocalDateTime windowStart = offer.getDepartureTime().minusHours(12L);
                LocalDateTime windowEnd = offer.getDepartureTime().plusHours(12L);
                RideRequest tempRequest = new RideRequest(passenger, origin, destination, windowStart, windowEnd);
                tempRequest.setId(-1L);
                if (!this.travelPolicyService.evaluate(offer.getDriver().getId(), offer, tempRequest).isAllowed()) continue;
                List<RideRequest> existing = TripPlanningController.activeBookings(offer);
                ArrayList tempRequests = new ArrayList(existing);
                tempRequests.add(tempRequest);
                try {
                    StopSequenceResult routingResult = this.stopPlanningService.planRoute(offer, tempRequests, vehicle);
                    boolean servesAll = routingResult.isFeasible() && routingResult.getSequence() != null && routingResult.getSequence().size() >= tempRequests.size() * 2 + 2;
                    if (!servesAll) continue;
                    RideContext pricingContext = new RideContext(offer.getDepartureTime(), origin, destination, offer.getOrigin(), offer.getDestination(), passenger.getReputationScore(), passenger.getIncentiveTier(), passengerDistance);
                    PricingResult pricingResult = this.pricingEngine.calculateFare(pricingContext, this.driverPricingRuleRepository.findByDriverIdAndEnabledTrueOrderByPriorityAsc(offer.getDriver().getId()));
                    HashMap match = new HashMap();
                    match.put("tripOfferId", offer.getId());
                    match.put("driverName", offer.getDriver().getName());
                    match.put("vehicleInfo", (vehicle.getMake() + " " + vehicle.getModel()));
                    match.put("departureTime", offer.getDepartureTime().toString());
                    match.put("totalTimeMinutes", routingResult.getTotalTimeMinutes());
                    match.put("totalDistanceKm", routingResult.getTotalDistanceKm());
                    match.put("spotsAvailable", Math.max(0, (vehicle.getCapacity() - existing.size())));
                    match.put("coPassengers", existing.size());
                    match.put("yourRide", this.yourRideMetrics(routingResult.getSequence(), passenger.getName(), passengerDistance));
                    match.put("routing", routingResult);
                    match.put("pricing", pricingResult);
                    matchingResults.add(match);
                }
                catch (MapApiException e) {
                    System.out.println("Skipping incompatible trip offer ID " + offer.getId() + " due to mapping error: " + e.getMessage());
                }
            }
        }
        catch (MapApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(("Google Maps API Error: " + e.getMessage()));
        }
        return ResponseEntity.ok(matchingResults);
    }

    /**
     * FR-8 booking flow: driver travel-policy veto (FR-15) -> persist -> DFS with ALL active
     * bookings (overbooking fix D4) -> driver-rule pricing (FR-16) -> payment -> notify.
     */
    @PostMapping(value={"/{tripOfferId}/book-passenger"})
    public ResponseEntity<?> bookPassengerOnTrip(@PathVariable Long tripOfferId, @RequestBody Map<String, String> payload) {
        HashMap response = new HashMap();
        String origin = payload.get("origin");
        String destination = payload.get("destination");
        String passengerIdStr = payload.get("passengerId");
        if (origin == null || destination == null || passengerIdStr == null) {
            return ResponseEntity.badRequest().body("Invalid payload parameters.");
        }
        try {
            StopSequenceResult routingResult;
            User driver;
            TravelPolicyService.PolicyDecision decision;
            LocalDateTime windowEnd;
            LocalDateTime windowStart;
            TripOffer offer = (TripOffer)this.tripOfferService.findById(tripOfferId).orElse(null);
            if (offer == null) {
                return ResponseEntity.badRequest().body(("TripOffer not found with id: " + tripOfferId));
            }
            Long passengerId = Long.parseLong(passengerIdStr);
            User passenger = (User)this.userService.findById(passengerId).orElse(null);
            if (passenger == null) {
                return ResponseEntity.badRequest().body(("Passenger user not found with id: " + passengerId));
            }
            boolean alreadyBooked = offer.getPassengers().stream().anyMatch(req -> req.getPassenger().getId().equals(passengerId));
            if (alreadyBooked) {
                return ResponseEntity.badRequest().body("You have already booked this trip.");
            }
            String windowStartStr = payload.get("pickupTimeWindowStart");
            String windowEndStr = payload.get("pickupTimeWindowEnd");
            if (windowStartStr == null || windowEndStr == null) {
                windowStart = offer.getDepartureTime().minusHours(12L);
                windowEnd = offer.getDepartureTime().plusHours(12L);
            } else {
                windowStart = LocalDateTime.parse(windowStartStr);
                windowEnd = LocalDateTime.parse(windowEndStr);
            }
            RideRequest rideRequest = new RideRequest(passenger, origin, destination, windowStart, windowEnd);
            String luggageStr = payload.get("luggageSize");
            if (luggageStr != null && !luggageStr.isBlank()) {
                try {
                    rideRequest.setLuggageSize(LuggageSize.valueOf(luggageStr));
                }
                catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid luggage size."));
                }
            }
            if (!(decision = this.travelPolicyService.evaluate((driver = offer.getDriver()).getId(), offer, rideRequest)).isAllowed()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Blocked by the driver's travel policy.", "violations", decision.getViolations()));
            }
            List<Vehicle> vehicles = this.vehicleService.findByDriverId(driver.getId());
            if (vehicles.isEmpty()) {
                return ResponseEntity.badRequest().body("Driver has no vehicle registered.");
            }
            Vehicle vehicle = (Vehicle)vehicles.get(0);
            rideRequest.setTripOffer(offer);
            rideRequest = this.rideRequestService.save(rideRequest);
            ArrayList<RideRequest> requests = new ArrayList<>(TripPlanningController.activeBookings(offer));
            boolean alreadyIncluded = false;
            for (RideRequest r : requests) {
                if (r.getId() == null || !r.getId().equals(rideRequest.getId())) continue;
                alreadyIncluded = true;
                break;
            }
            if (!alreadyIncluded) {
                requests.add(rideRequest);
            }
            boolean servesAll = (routingResult = this.stopPlanningService.planRoute(offer, requests, vehicle)).isFeasible() && routingResult.getSequence() != null && routingResult.getSequence().size() >= requests.size() * 2 + 2;
            response.put("routing", routingResult);
            if (!servesAll) {
                this.rideRequestService.delete(rideRequest.getId());
                response.put("bookingStatus", "FAILED_ROUTING");
                return ResponseEntity.ok(response);
            }
            double passengerDistance = this.mappingService.getDistanceKm(rideRequest.getOrigin(), rideRequest.getDestination());
            RideContext pricingContext = new RideContext(offer.getDepartureTime(), rideRequest.getOrigin(), rideRequest.getDestination(), offer.getOrigin(), offer.getDestination(), passenger.getReputationScore(), passenger.getIncentiveTier(), passengerDistance);
            PricingResult pricingResult = this.pricingEngine.calculateFare(pricingContext, this.driverPricingRuleRepository.findByDriverIdAndEnabledTrueOrderByPriorityAsc(driver.getId()));
            boolean identityVerified = this.paymentService.verifyIdentity(passenger.getId());
            boolean transactionCleared = false;
            if (identityVerified) {
                transactionCleared = this.paymentService.processPayment(passenger.getId(), driver.getId(), pricingResult.getFinalFare());
            }
            ArrayList passengersBreakdown = new ArrayList();
            HashMap pDetail = new HashMap();
            pDetail.put("passengerId", passenger.getId());
            pDetail.put("passengerName", passenger.getName());
            pDetail.put("reputationScore", passenger.getReputationScore());
            pDetail.put("incentiveTier", passenger.getIncentiveTier());
            pDetail.put("pricing", pricingResult);
            pDetail.put("identityVerified", identityVerified);
            pDetail.put("paymentCleared", transactionCleared);
            // Ledger (FR-8): persist a referenced record of what the gateway reported
            com.routeshare.model.PaymentTransaction receipt = this.paymentLedgerService.record(
                    passenger, driver, pricingResult.getFinalFare(),
                    identityVerified && transactionCleared
                            ? com.routeshare.model.PaymentTransaction.Status.COMPLETED
                            : com.routeshare.model.PaymentTransaction.Status.HELD,
                    rideRequest.getOrigin() + " → " + rideRequest.getDestination(),
                    rideRequest.getId());
            HashMap paymentInfo = new HashMap();
            paymentInfo.put("reference", receipt.getReference());
            paymentInfo.put("status", receipt.getStatus().toString());
            paymentInfo.put("amount", receipt.getAmount());
            pDetail.put("payment", paymentInfo);
            passengersBreakdown.add(pDetail);
            response.put("passengers", passengersBreakdown);
            boolean bookingSuccessful = identityVerified && transactionCleared;
            response.put("bookingStatus", (bookingSuccessful ? "SUCCESSFUL" : "PAYMENT_HOLD"));
            rideRequest.setStatus(bookingSuccessful ? BookingStatus.CONFIRMED : BookingStatus.PENDING);
            this.rideRequestService.save(rideRequest);
            this.notificationService.notify(driver, NotificationType.BOOKING, passenger.getName() + " booked a seat on your trip " + offer.getOrigin() + " -> " + offer.getDestination() + (bookingSuccessful ? " (payment cleared)." : " (payment on hold)."));
            return ResponseEntity.ok(response);
        }
        catch (MapApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(("Google Maps API Error: " + e.getMessage()));
        }
    }

    /**
     * Driver cockpit (FR-5/FR-7): the CURRENT route of a trip — a side-effect-free
     * re-plan over all active bookings, with metrics and map-ready waypoints.
     */
    @org.springframework.web.bind.annotation.GetMapping(value={"/{tripOfferId}/route"})
    public ResponseEntity<?> currentRoute(@PathVariable Long tripOfferId) {
        TripOffer offer = (TripOffer)this.tripOfferService.findById(tripOfferId).orElse(null);
        if (offer == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "TripOffer not found with id: " + tripOfferId));
        }
        List<Vehicle> vehicles = this.vehicleService.findByDriverId(offer.getDriver().getId());
        if (vehicles.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Driver has no vehicle registered."));
        }
        try {
            List<RideRequest> active = TripPlanningController.activeBookings(offer);
            StopSequenceResult plan = this.stopPlanningService.planRoute(offer, active, vehicles.get(0));
            int directTime = this.mappingService.getTravelTimeMinutes(offer.getOrigin(), offer.getDestination());
            java.util.List<String> waypoints = new ArrayList<>();
            if (plan.getSequence() != null) {
                for (String label : plan.getSequence()) {
                    int at = label.lastIndexOf(" at ");
                    if (at >= 0) { waypoints.add(label.substring(at + 4)); continue; }
                    int colon = label.indexOf(": ");
                    waypoints.add(colon >= 0 ? label.substring(colon + 2) : label);
                }
            }
            HashMap body = new HashMap();
            body.put("tripOfferId", offer.getId());
            body.put("routing", plan);
            body.put("waypoints", waypoints);
            body.put("directTimeMinutes", directTime);
            body.put("detourMinutes", Math.max(0, plan.getTotalTimeMinutes() - directTime));
            body.put("activeBookings", active.size());
            return ResponseEntity.ok(body);
        }
        catch (MapApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Google Maps API Error: " + e.getMessage()));
        }
    }

    /** Checks whether a driver's existing offers can accommodate a specific ride request. */
    @PostMapping(value={"/check-existing-matches"})
    public ResponseEntity<?> checkExistingMatches(@RequestBody Map<String, String> payload) {
        String driverIdStr = payload.get("driverId");
        String rideRequestIdStr = payload.get("rideRequestId");
        if (driverIdStr == null || rideRequestIdStr == null) {
            return ResponseEntity.badRequest().body("Invalid parameters.");
        }
        try {
            Long driverId = Long.parseLong(driverIdStr);
            Long rideRequestId = Long.parseLong(rideRequestIdStr);
            RideRequest request = (RideRequest)this.rideRequestService.findById(rideRequestId).orElse(null);
            if (request == null) {
                return ResponseEntity.badRequest().body("RideRequest not found.");
            }
            List<Vehicle> vehicles = this.vehicleService.findByDriverId(driverId);
            if (vehicles.isEmpty()) {
                return ResponseEntity.badRequest().body("Driver has no vehicle registered.");
            }
            Vehicle vehicle = (Vehicle)vehicles.get(0);
            List<TripOffer> driverOffers = this.tripOfferService.findAll().stream().filter(o -> o.getDriver().getId().equals(driverId)).toList();
            ArrayList matchingResults = new ArrayList();
            List testRequests = List.of(request);
            for (TripOffer offer : driverOffers) {
                try {
                    StopSequenceResult routingResult = this.stopPlanningService.planRoute(offer, testRequests, vehicle);
                    if (!routingResult.isFeasible()) continue;
                    HashMap match = new HashMap();
                    match.put("tripOfferId", offer.getId());
                    match.put("origin", offer.getOrigin());
                    match.put("destination", offer.getDestination());
                    match.put("departureTime", offer.getDepartureTime().toString());
                    match.put("totalTimeMinutes", routingResult.getTotalTimeMinutes());
                    match.put("totalDistanceKm", routingResult.getTotalDistanceKm());
                    matchingResults.add(match);
                }
                catch (MapApiException e) {
                    System.out.println("Skipping incompatible driver offer ID " + offer.getId() + " due to mapping error: " + e.getMessage());
                }
            }
            return ResponseEntity.ok(matchingResults);
        }
        catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Error processing matches: " + e.getMessage()));
        }
    }
}
