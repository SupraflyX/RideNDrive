package com.routeshare.controller;

import com.routeshare.exception.MapApiException;
import com.routeshare.model.PaymentTransaction;
import com.routeshare.model.RideRequest;
import com.routeshare.model.TripOffer;
import com.routeshare.model.User;
import com.routeshare.model.Vehicle;
import com.routeshare.model.dto.PlannedStop;
import com.routeshare.model.dto.PricingResult;
import com.routeshare.model.dto.StopSequenceResult;
import com.routeshare.model.enums.BookingStatus;
import com.routeshare.model.enums.LuggageSize;
import com.routeshare.model.enums.NotificationType;
import com.routeshare.repository.DriverPricingRuleRepository;
import com.routeshare.service.NotificationService;
import com.routeshare.service.PaymentLedgerService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TripPlanningController orchestrates the end-to-end carpool workflow connecting database entities,
 * routing services, driver policies, pricing strategy chains, and payment processing.
 *
 * Note: recovered from bytecode after a disk failure; the behaviour is the
 * compiled Sprint 9 behaviour, since re-expressed in idiomatic Java.
 */
@RestController
@RequestMapping("/api/trips")
public class TripPlanningController {

    private static final Logger log = LoggerFactory.getLogger(TripPlanningController.class);

    /** Default pickup window when the caller does not supply one: ±12h around departure. */
    private static final long DEFAULT_WINDOW_HOURS = 12L;

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
    private final PaymentLedgerService paymentLedgerService;

    @Autowired
    public TripPlanningController(TripOfferService tripOfferService,
                                  RideRequestService rideRequestService,
                                  VehicleService vehicleService,
                                  StopPlanningService stopPlanningService,
                                  PricingEngine pricingEngine,
                                  MappingService mappingService,
                                  PaymentService paymentService,
                                  UserService userService,
                                  NotificationService notificationService,
                                  TravelPolicyService travelPolicyService,
                                  DriverPricingRuleRepository driverPricingRuleRepository,
                                  PaymentLedgerService paymentLedgerService) {
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

    // ── shared helpers ───────────────────────────────────────────────

    /** Active bookings (PENDING or CONFIRMED) currently attached to an offer. */
    private static List<RideRequest> activeBookings(TripOffer offer) {
        List<RideRequest> active = new ArrayList<>();
        if (offer.getPassengers() != null) {
            for (RideRequest r : offer.getPassengers()) {
                BookingStatus status = r.getStatus() == null ? BookingStatus.PENDING : r.getStatus();
                if (status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED) {
                    active.add(r);
                }
            }
        }
        return active;
    }

    /**
     * A plan serves every request only if it is feasible AND its sequence contains a
     * pickup and a dropoff for each one, plus the driver's own origin and destination.
     */
    private static boolean servesAll(StopSequenceResult plan, int requestCount) {
        return plan.isFeasible()
                && plan.getSequence() != null
                && plan.getSequence().size() >= requestCount * 2 + 2;
    }

    /**
     * Passenger-perspective metrics (FR-14): the searcher cares about THEIR leg,
     * not the driver's whole trip. Walks the planned stops from the searcher's
     * PICKUP to their DROPOFF, summing leg times (intermediate stops included).
     */
    private Map<String, Object> yourRideMetrics(List<PlannedStop> stops, String passengerName,
                                                double directDistanceKm) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("distanceKm", Math.round(directDistanceKm * 10.0) / 10.0);
        if (stops == null) {
            return metrics;
        }

        int pickupIdx = -1;
        int dropoffIdx = -1;
        for (int i = 0; i < stops.size(); i++) {
            PlannedStop stop = stops.get(i);
            if (!passengerName.equals(stop.getPassengerName())) {
                continue;
            }
            if (stop.getKind() == PlannedStop.Kind.PICKUP) {
                pickupIdx = i;
            } else if (stop.getKind() == PlannedStop.Kind.DROPOFF) {
                dropoffIdx = i;
            }
        }

        if (pickupIdx >= 0 && dropoffIdx > pickupIdx) {
            int inCar = 0;
            for (int i = pickupIdx; i < dropoffIdx; i++) {
                inCar += mappingService.getTravelTimeMinutes(
                        stops.get(i).getLocation(), stops.get(i + 1).getLocation());
            }
            metrics.put("inCarTimeMinutes", inCar);
            metrics.put("stopsBeforeDropoff", Math.max(0, dropoffIdx - pickupIdx - 1));
        }
        return metrics;
    }

    // ── endpoints ────────────────────────────────────────────────────

    /**
     * FR-14: feasibility-aware search - every candidate offer is vetted by the driver's
     * travel policy (FR-15) and planned TOGETHER with its active bookings (overbooking-aware).
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

        LocalDate searchDate = LocalDate.parse(dateStr);
        List<Map<String, Object>> matchingResults = new ArrayList<>();
        try {
            double passengerDistance = mappingService.getDistanceKm(origin, destination);
            for (TripOffer offer : tripOfferService.findAll()) {
                if (!offer.getDepartureTime().toLocalDate().equals(searchDate)) {
                    continue;
                }
                List<Vehicle> vehicles = vehicleService.findByDriverId(offer.getDriver().getId());
                if (vehicles.isEmpty()) {
                    continue;
                }
                Vehicle vehicle = vehicles.get(0);

                RideRequest tempRequest = new RideRequest(passenger, origin, destination,
                        offer.getDepartureTime().minusHours(DEFAULT_WINDOW_HOURS),
                        offer.getDepartureTime().plusHours(DEFAULT_WINDOW_HOURS));
                tempRequest.setId(-1L);
                if (!travelPolicyService.evaluate(offer.getDriver().getId(), offer, tempRequest).isAllowed()) {
                    continue;
                }

                List<RideRequest> existing = activeBookings(offer);
                List<RideRequest> tempRequests = new ArrayList<>(existing);
                tempRequests.add(tempRequest);
                try {
                    StopSequenceResult routingResult = stopPlanningService.planRoute(offer, tempRequests, vehicle);
                    if (!servesAll(routingResult, tempRequests.size())) {
                        continue;
                    }

                    RideContext pricingContext = new RideContext(offer.getDepartureTime(), origin, destination,
                            offer.getOrigin(), offer.getDestination(), passenger.getReputationScore(),
                            passenger.getIncentiveTier(), passengerDistance);
                    PricingResult pricingResult = pricingEngine.calculateFare(pricingContext,
                            driverPricingRuleRepository.findByDriverIdAndEnabledTrueOrderByPriorityAsc(offer.getDriver().getId()));

                    Map<String, Object> match = new LinkedHashMap<>();
                    match.put("tripOfferId", offer.getId());
                    match.put("driverName", offer.getDriver().getName());
                    match.put("vehicleInfo", vehicle.getMake() + " " + vehicle.getModel());
                    match.put("departureTime", offer.getDepartureTime().toString());
                    match.put("totalTimeMinutes", routingResult.getTotalTimeMinutes());
                    match.put("totalDistanceKm", routingResult.getTotalDistanceKm());
                    match.put("spotsAvailable", Math.max(0, vehicle.getCapacity() - existing.size()));
                    match.put("coPassengers", existing.size());
                    match.put("yourRide", yourRideMetrics(routingResult.getStops(), passenger.getName(), passengerDistance));
                    match.put("routing", routingResult);
                    match.put("pricing", pricingResult);
                    matchingResults.add(match);
                } catch (MapApiException e) {
                    log.info("Skipping incompatible trip offer ID {} due to mapping error: {}", offer.getId(), e.getMessage());
                }
            }
        } catch (MapApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Google Maps API Error: " + e.getMessage());
        }
        return ResponseEntity.ok(matchingResults);
    }

    /**
     * FR-8 booking flow: driver travel-policy veto (FR-15) -> persist -> DFS with ALL active
     * bookings (overbooking fix D4) -> driver-rule pricing (FR-16) -> payment -> notify.
     */
    @PostMapping("/{tripOfferId}/book-passenger")
    public ResponseEntity<?> bookPassengerOnTrip(@PathVariable Long tripOfferId,
                                                 @RequestBody Map<String, String> payload) {
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

            boolean alreadyBooked = offer.getPassengers().stream()
                    .anyMatch(req -> req.getPassenger().getId().equals(passengerId));
            if (alreadyBooked) {
                return ResponseEntity.badRequest().body("You have already booked this trip.");
            }

            String windowStartStr = payload.get("pickupTimeWindowStart");
            String windowEndStr = payload.get("pickupTimeWindowEnd");
            LocalDateTime windowStart;
            LocalDateTime windowEnd;
            if (windowStartStr == null || windowEndStr == null) {
                windowStart = offer.getDepartureTime().minusHours(DEFAULT_WINDOW_HOURS);
                windowEnd = offer.getDepartureTime().plusHours(DEFAULT_WINDOW_HOURS);
            } else {
                windowStart = LocalDateTime.parse(windowStartStr);
                windowEnd = LocalDateTime.parse(windowEndStr);
            }
            if (windowEnd.isBefore(LocalDateTime.now())) {
                return ResponseEntity.badRequest().body(Map.of("error", "The pickup time window has already passed."));
            }
            if (windowEnd.isBefore(windowStart)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Pickup window end must be after its start."));
            }

            RideRequest rideRequest = new RideRequest(passenger, origin, destination, windowStart, windowEnd);
            String luggageStr = payload.get("luggageSize");
            if (luggageStr != null && !luggageStr.isBlank()) {
                try {
                    rideRequest.setLuggageSize(LuggageSize.valueOf(luggageStr));
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid luggage size."));
                }
            }

            User driver = offer.getDriver();
            TravelPolicyService.PolicyDecision decision =
                    travelPolicyService.evaluate(driver.getId(), offer, rideRequest);
            if (!decision.isAllowed()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "Blocked by the driver's travel policy.",
                        "violations", decision.getViolations()));
            }

            List<Vehicle> vehicles = vehicleService.findByDriverId(driver.getId());
            if (vehicles.isEmpty()) {
                return ResponseEntity.badRequest().body("Driver has no vehicle registered.");
            }
            Vehicle vehicle = vehicles.get(0);

            rideRequest.setTripOffer(offer);
            rideRequest = rideRequestService.save(rideRequest);

            // Re-plan over ALL active bookings, not just this one (overbooking fix D4).
            List<RideRequest> requests = new ArrayList<>(activeBookings(offer));
            RideRequest saved = rideRequest;
            boolean alreadyIncluded = requests.stream()
                    .anyMatch(r -> r.getId() != null && r.getId().equals(saved.getId()));
            if (!alreadyIncluded) {
                requests.add(rideRequest);
            }

            StopSequenceResult routingResult = stopPlanningService.planRoute(offer, requests, vehicle);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("routing", routingResult);
            if (!servesAll(routingResult, requests.size())) {
                rideRequestService.delete(rideRequest.getId());
                response.put("bookingStatus", "FAILED_ROUTING");
                return ResponseEntity.ok(response);
            }

            double passengerDistance = mappingService.getDistanceKm(rideRequest.getOrigin(), rideRequest.getDestination());
            RideContext pricingContext = new RideContext(offer.getDepartureTime(),
                    rideRequest.getOrigin(), rideRequest.getDestination(),
                    offer.getOrigin(), offer.getDestination(),
                    passenger.getReputationScore(), passenger.getIncentiveTier(), passengerDistance);
            PricingResult pricingResult = pricingEngine.calculateFare(pricingContext,
                    driverPricingRuleRepository.findByDriverIdAndEnabledTrueOrderByPriorityAsc(driver.getId()));

            boolean identityVerified = paymentService.verifyIdentity(passenger.getId());
            boolean transactionCleared = identityVerified
                    && paymentService.processPayment(passenger.getId(), driver.getId(), pricingResult.getFinalFare());
            boolean bookingSuccessful = identityVerified && transactionCleared;

            // Ledger (FR-8): persist a referenced record of what the gateway reported
            PaymentTransaction receipt = paymentLedgerService.record(
                    passenger, driver, pricingResult.getFinalFare(),
                    bookingSuccessful ? PaymentTransaction.Status.COMPLETED : PaymentTransaction.Status.HELD,
                    rideRequest.getOrigin() + " → " + rideRequest.getDestination(),
                    rideRequest.getId());

            Map<String, Object> paymentInfo = new LinkedHashMap<>();
            paymentInfo.put("reference", receipt.getReference());
            paymentInfo.put("status", receipt.getStatus().toString());
            paymentInfo.put("amount", receipt.getAmount());

            Map<String, Object> passengerDetail = new LinkedHashMap<>();
            passengerDetail.put("passengerId", passenger.getId());
            passengerDetail.put("passengerName", passenger.getName());
            passengerDetail.put("reputationScore", passenger.getReputationScore());
            passengerDetail.put("incentiveTier", passenger.getIncentiveTier());
            passengerDetail.put("pricing", pricingResult);
            passengerDetail.put("identityVerified", identityVerified);
            passengerDetail.put("paymentCleared", transactionCleared);
            passengerDetail.put("payment", paymentInfo);

            response.put("passengers", List.of(passengerDetail));
            response.put("bookingStatus", bookingSuccessful ? "SUCCESSFUL" : "PAYMENT_HOLD");

            rideRequest.setStatus(bookingSuccessful ? BookingStatus.CONFIRMED : BookingStatus.PENDING);
            rideRequestService.save(rideRequest);
            notificationService.notify(driver, NotificationType.BOOKING,
                    passenger.getName() + " booked a seat on your trip "
                            + offer.getOrigin() + " -> " + offer.getDestination()
                            + (bookingSuccessful ? " (payment cleared)." : " (payment on hold)."));
            return ResponseEntity.ok(response);
        } catch (MapApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Google Maps API Error: " + e.getMessage());
        }
    }

    /**
     * Driver cockpit (FR-5/FR-7): the CURRENT route of a trip — a side-effect-free
     * re-plan over all active bookings, with metrics and map-ready waypoints.
     */
    @GetMapping("/{tripOfferId}/route")
    public ResponseEntity<?> currentRoute(@PathVariable Long tripOfferId) {
        TripOffer offer = tripOfferService.findById(tripOfferId).orElse(null);
        if (offer == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "TripOffer not found with id: " + tripOfferId));
        }
        List<Vehicle> vehicles = vehicleService.findByDriverId(offer.getDriver().getId());
        if (vehicles.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Driver has no vehicle registered."));
        }

        try {
            List<RideRequest> active = activeBookings(offer);
            StopSequenceResult plan = stopPlanningService.planRoute(offer, active, vehicles.get(0));
            int directTime = mappingService.getTravelTimeMinutes(offer.getOrigin(), offer.getDestination());

            List<String> waypoints = plan.getStops() == null
                    ? List.of()
                    : plan.getStops().stream().map(PlannedStop::getLocation).toList();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tripOfferId", offer.getId());
            body.put("routing", plan);
            body.put("waypoints", waypoints);
            body.put("directTimeMinutes", directTime);
            body.put("detourMinutes", Math.max(0, plan.getTotalTimeMinutes() - directTime));
            body.put("activeBookings", active.size());
            return ResponseEntity.ok(body);
        } catch (MapApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Google Maps API Error: " + e.getMessage()));
        }
    }

    /** Checks whether a driver's existing offers can accommodate a specific ride request. */
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
            List<RideRequest> testRequests = List.of(request);

            List<Map<String, Object>> matchingResults = new ArrayList<>();
            for (TripOffer offer : driverOffers) {
                try {
                    StopSequenceResult routingResult = stopPlanningService.planRoute(offer, testRequests, vehicle);
                    if (!routingResult.isFeasible()) {
                        continue;
                    }
                    Map<String, Object> match = new LinkedHashMap<>();
                    match.put("tripOfferId", offer.getId());
                    match.put("origin", offer.getOrigin());
                    match.put("destination", offer.getDestination());
                    match.put("departureTime", offer.getDepartureTime().toString());
                    match.put("totalTimeMinutes", routingResult.getTotalTimeMinutes());
                    match.put("totalDistanceKm", routingResult.getTotalDistanceKm());
                    matchingResults.add(match);
                } catch (MapApiException e) {
                    log.info("Skipping incompatible driver offer ID {} due to mapping error: {}", offer.getId(), e.getMessage());
                }
            }
            return ResponseEntity.ok(matchingResults);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing matches: " + e.getMessage());
        }
    }
}
