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

    @Autowired
    public TripPlanningController(TripOfferService tripOfferService, RideRequestService rideRequestService, VehicleService vehicleService, StopPlanningService stopPlanningService, PricingEngine pricingEngine, MappingService mappingService, PaymentService paymentService, UserService userService, NotificationService notificationService, TravelPolicyService travelPolicyService, DriverPricingRuleRepository driverPricingRuleRepository) {
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
    }

    private static List<RideRequest> activeBookings(TripOffer offer) {
        ArrayList active = new ArrayList();
        if (offer.getPassengers() != null) {
            for (RideRequest r : offer.getPassengers()) {
                BookingStatus s;
                BookingStatus bookingStatus = s = r.getStatus() == null ? BookingStatus.PENDING : r.getStatus();
                if (s != BookingStatus.PENDING && s != BookingStatus.CONFIRMED) continue;
                active.add((Object)r);
            }
        }
        return active;
    }

    @PostMapping(value={"/{tripOfferId}/plan"})
    public ResponseEntity<?> planAndBookTrip(@PathVariable Long tripOfferId, @RequestBody List<Long> requestIds) {
        HashMap response = new HashMap();
        try {
            TripOffer offer = (TripOffer)this.tripOfferService.findById(tripOfferId).orElse(null);
            if (offer == null) {
                return ResponseEntity.badRequest().body((Object)("TripOffer not found with id: " + tripOfferId));
            }
            User driver = offer.getDriver();
            List<Vehicle> vehicles = this.vehicleService.findByDriverId(driver.getId());
            if (vehicles.isEmpty()) {
                return ResponseEntity.badRequest().body((Object)"Driver has no vehicle registered.");
            }
            Vehicle vehicle = (Vehicle)vehicles.get(0);
            ArrayList<RideRequest> requests = new ArrayList<>();
            for (Long rId : requestIds) {
                this.rideRequestService.findById(rId).ifPresent(arg_0 -> ((List)requests).add(arg_0));
            }
            StopSequenceResult routingResult = this.stopPlanningService.planRoute(offer, (List<RideRequest>)requests, vehicle);
            response.put((Object)"routing", (Object)routingResult);
            if (!routingResult.isFeasible()) {
                response.put((Object)"bookingStatus", (Object)"FAILED_ROUTING");
                return ResponseEntity.ok((Object)response);
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
                pDetail.put((Object)"passengerId", (Object)passenger.getId());
                pDetail.put((Object)"passengerName", (Object)passenger.getName());
                pDetail.put((Object)"reputationScore", (Object)passenger.getReputationScore());
                pDetail.put((Object)"incentiveTier", (Object)passenger.getIncentiveTier());
                pDetail.put((Object)"pricing", (Object)pricingResult);
                pDetail.put((Object)"identityVerified", (Object)identityVerified);
                pDetail.put((Object)"paymentCleared", (Object)transactionCleared);
                passengersBreakdown.add((Object)pDetail);
            }
            response.put((Object)"passengers", (Object)passengersBreakdown);
            response.put((Object)"bookingStatus", (Object)(paymentSuccess ? "SUCCESSFUL" : "PAYMENT_HOLD"));
            return ResponseEntity.ok((Object)response);
        }
        catch (MapApiException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body((Object)("Google Maps API Error: " + e.getMessage()));
        }
    }

    @PostMapping(value={"/search-matches"})
    public ResponseEntity<?> searchMatchingTrips(@RequestBody Map<String, String> payload) {
        String origin = (String)payload.get((Object)"origin");
        String destination = (String)payload.get((Object)"destination");
        String dateStr = (String)payload.get((Object)"date");
        String passengerIdStr = (String)payload.get((Object)"passengerId");
        if (origin == null || destination == null || passengerIdStr == null || dateStr == null) {
            return ResponseEntity.badRequest().body((Object)"Invalid search parameters.");
        }
        Long passengerId = Long.parseLong((String)passengerIdStr);
        User passenger = (User)this.userService.findById(passengerId).orElse(null);
        if (passenger == null) {
            return ResponseEntity.badRequest().body((Object)"Passenger user not found.");
        }
        LocalDate searchDate = LocalDate.parse((CharSequence)dateStr);
        List<TripOffer> allOffers = this.tripOfferService.findAll();
        ArrayList matchingResults = new ArrayList();
        try {
            double passengerDistance = this.mappingService.getDistanceKm(origin, destination);
            for (TripOffer offer : allOffers) {
                List<Vehicle> vehicles;
                if (!offer.getDepartureTime().toLocalDate().equals((Object)searchDate) || (vehicles = this.vehicleService.findByDriverId(offer.getDriver().getId())).isEmpty()) continue;
                Vehicle vehicle = (Vehicle)vehicles.get(0);
                LocalDateTime windowStart = offer.getDepartureTime().minusHours(12L);
                LocalDateTime windowEnd = offer.getDepartureTime().plusHours(12L);
                RideRequest tempRequest = new RideRequest(passenger, origin, destination, windowStart, windowEnd);
                tempRequest.setId(-1L);
                if (!this.travelPolicyService.evaluate(offer.getDriver().getId(), offer, tempRequest).isAllowed()) continue;
                List<RideRequest> existing = TripPlanningController.activeBookings(offer);
                ArrayList tempRequests = new ArrayList(existing);
                tempRequests.add((Object)tempRequest);
                try {
                    StopSequenceResult routingResult = this.stopPlanningService.planRoute(offer, (List<RideRequest>)tempRequests, vehicle);
                    boolean servesAll = routingResult.isFeasible() && routingResult.getSequence() != null && routingResult.getSequence().size() >= tempRequests.size() * 2 + 2;
                    if (!servesAll) continue;
                    RideContext pricingContext = new RideContext(offer.getDepartureTime(), origin, destination, offer.getOrigin(), offer.getDestination(), passenger.getReputationScore(), passenger.getIncentiveTier(), passengerDistance);
                    PricingResult pricingResult = this.pricingEngine.calculateFare(pricingContext, this.driverPricingRuleRepository.findByDriverIdAndEnabledTrueOrderByPriorityAsc(offer.getDriver().getId()));
                    HashMap match = new HashMap();
                    match.put((Object)"tripOfferId", (Object)offer.getId());
                    match.put((Object)"driverName", (Object)offer.getDriver().getName());
                    match.put((Object)"vehicleInfo", (Object)(vehicle.getMake() + " " + vehicle.getModel()));
                    match.put((Object)"departureTime", (Object)offer.getDepartureTime().toString());
                    match.put((Object)"totalTimeMinutes", (Object)routingResult.getTotalTimeMinutes());
                    match.put((Object)"totalDistanceKm", (Object)routingResult.getTotalDistanceKm());
                    match.put((Object)"spotsAvailable", (Object)Math.max((int)0, (int)(vehicle.getCapacity() - existing.size())));
                    match.put((Object)"routing", (Object)routingResult);
                    match.put((Object)"pricing", (Object)pricingResult);
                    matchingResults.add((Object)match);
                }
                catch (MapApiException e) {
                    System.out.println("Skipping incompatible trip offer ID " + offer.getId() + " due to mapping error: " + e.getMessage());
                }
            }
        }
        catch (MapApiException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body((Object)("Google Maps API Error: " + e.getMessage()));
        }
        return ResponseEntity.ok((Object)matchingResults);
    }

    @PostMapping(value={"/{tripOfferId}/book-passenger"})
    public ResponseEntity<?> bookPassengerOnTrip(@PathVariable Long tripOfferId, @RequestBody Map<String, String> payload) {
        HashMap response = new HashMap();
        String origin = (String)payload.get((Object)"origin");
        String destination = (String)payload.get((Object)"destination");
        String passengerIdStr = (String)payload.get((Object)"passengerId");
        if (origin == null || destination == null || passengerIdStr == null) {
            return ResponseEntity.badRequest().body((Object)"Invalid payload parameters.");
        }
        try {
            StopSequenceResult routingResult;
            User driver;
            TravelPolicyService.PolicyDecision decision;
            LocalDateTime windowEnd;
            LocalDateTime windowStart;
            TripOffer offer = (TripOffer)this.tripOfferService.findById(tripOfferId).orElse(null);
            if (offer == null) {
                return ResponseEntity.badRequest().body((Object)("TripOffer not found with id: " + tripOfferId));
            }
            Long passengerId = Long.parseLong((String)passengerIdStr);
            User passenger = (User)this.userService.findById(passengerId).orElse(null);
            if (passenger == null) {
                return ResponseEntity.badRequest().body((Object)("Passenger user not found with id: " + passengerId));
            }
            boolean alreadyBooked = offer.getPassengers().stream().anyMatch(req -> req.getPassenger().getId().equals((Object)passengerId));
            if (alreadyBooked) {
                return ResponseEntity.badRequest().body((Object)"You have already booked this trip.");
            }
            String windowStartStr = (String)payload.get((Object)"pickupTimeWindowStart");
            String windowEndStr = (String)payload.get((Object)"pickupTimeWindowEnd");
            if (windowStartStr == null || windowEndStr == null) {
                windowStart = offer.getDepartureTime().minusHours(12L);
                windowEnd = offer.getDepartureTime().plusHours(12L);
            } else {
                windowStart = LocalDateTime.parse((CharSequence)windowStartStr);
                windowEnd = LocalDateTime.parse((CharSequence)windowEndStr);
            }
            RideRequest rideRequest = new RideRequest(passenger, origin, destination, windowStart, windowEnd);
            String luggageStr = (String)payload.get((Object)"luggageSize");
            if (luggageStr != null && !luggageStr.isBlank()) {
                try {
                    rideRequest.setLuggageSize(LuggageSize.valueOf(luggageStr));
                }
                catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body((Object)Map.of((Object)"error", (Object)"Invalid luggage size."));
                }
            }
            if (!(decision = this.travelPolicyService.evaluate((driver = offer.getDriver()).getId(), offer, rideRequest)).isAllowed()) {
                return ResponseEntity.status((HttpStatusCode)HttpStatus.FORBIDDEN).body((Object)Map.of((Object)"error", (Object)"Blocked by the driver's travel policy.", (Object)"violations", decision.getViolations()));
            }
            List<Vehicle> vehicles = this.vehicleService.findByDriverId(driver.getId());
            if (vehicles.isEmpty()) {
                return ResponseEntity.badRequest().body((Object)"Driver has no vehicle registered.");
            }
            Vehicle vehicle = (Vehicle)vehicles.get(0);
            rideRequest.setTripOffer(offer);
            rideRequest = this.rideRequestService.save(rideRequest);
            ArrayList<RideRequest> requests = new ArrayList<>(TripPlanningController.activeBookings(offer));
            boolean alreadyIncluded = false;
            for (RideRequest r : requests) {
                if (r.getId() == null || !r.getId().equals((Object)rideRequest.getId())) continue;
                alreadyIncluded = true;
                break;
            }
            if (!alreadyIncluded) {
                requests.add(rideRequest);
            }
            boolean servesAll = (routingResult = this.stopPlanningService.planRoute(offer, (List<RideRequest>)requests, vehicle)).isFeasible() && routingResult.getSequence() != null && routingResult.getSequence().size() >= requests.size() * 2 + 2;
            response.put((Object)"routing", (Object)routingResult);
            if (!servesAll) {
                this.rideRequestService.delete(rideRequest.getId());
                response.put((Object)"bookingStatus", (Object)"FAILED_ROUTING");
                return ResponseEntity.ok((Object)response);
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
            pDetail.put((Object)"passengerId", (Object)passenger.getId());
            pDetail.put((Object)"passengerName", (Object)passenger.getName());
            pDetail.put((Object)"reputationScore", (Object)passenger.getReputationScore());
            pDetail.put((Object)"incentiveTier", (Object)passenger.getIncentiveTier());
            pDetail.put((Object)"pricing", (Object)pricingResult);
            pDetail.put((Object)"identityVerified", (Object)identityVerified);
            pDetail.put((Object)"paymentCleared", (Object)transactionCleared);
            passengersBreakdown.add((Object)pDetail);
            response.put((Object)"passengers", (Object)passengersBreakdown);
            boolean bookingSuccessful = identityVerified && transactionCleared;
            response.put((Object)"bookingStatus", (Object)(bookingSuccessful ? "SUCCESSFUL" : "PAYMENT_HOLD"));
            rideRequest.setStatus(bookingSuccessful ? BookingStatus.CONFIRMED : BookingStatus.PENDING);
            this.rideRequestService.save(rideRequest);
            this.notificationService.notify(driver, NotificationType.BOOKING, passenger.getName() + " booked a seat on your trip " + offer.getOrigin() + " -> " + offer.getDestination() + (bookingSuccessful ? " (payment cleared)." : " (payment on hold)."));
            return ResponseEntity.ok((Object)response);
        }
        catch (MapApiException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body((Object)("Google Maps API Error: " + e.getMessage()));
        }
    }

    @PostMapping(value={"/check-existing-matches"})
    public ResponseEntity<?> checkExistingMatches(@RequestBody Map<String, String> payload) {
        String driverIdStr = (String)payload.get((Object)"driverId");
        String rideRequestIdStr = (String)payload.get((Object)"rideRequestId");
        if (driverIdStr == null || rideRequestIdStr == null) {
            return ResponseEntity.badRequest().body((Object)"Invalid parameters.");
        }
        try {
            Long driverId = Long.parseLong((String)driverIdStr);
            Long rideRequestId = Long.parseLong((String)rideRequestIdStr);
            RideRequest request = (RideRequest)this.rideRequestService.findById(rideRequestId).orElse(null);
            if (request == null) {
                return ResponseEntity.badRequest().body((Object)"RideRequest not found.");
            }
            List<Vehicle> vehicles = this.vehicleService.findByDriverId(driverId);
            if (vehicles.isEmpty()) {
                return ResponseEntity.badRequest().body((Object)"Driver has no vehicle registered.");
            }
            Vehicle vehicle = (Vehicle)vehicles.get(0);
            List<TripOffer> driverOffers = this.tripOfferService.findAll().stream().filter(o -> o.getDriver().getId().equals((Object)driverId)).toList();
            ArrayList matchingResults = new ArrayList();
            List testRequests = List.of((Object)request);
            for (TripOffer offer : driverOffers) {
                try {
                    StopSequenceResult routingResult = this.stopPlanningService.planRoute(offer, (List<RideRequest>)testRequests, vehicle);
                    if (!routingResult.isFeasible()) continue;
                    HashMap match = new HashMap();
                    match.put((Object)"tripOfferId", (Object)offer.getId());
                    match.put((Object)"origin", (Object)offer.getOrigin());
                    match.put((Object)"destination", (Object)offer.getDestination());
                    match.put((Object)"departureTime", (Object)offer.getDepartureTime().toString());
                    match.put((Object)"totalTimeMinutes", (Object)routingResult.getTotalTimeMinutes());
                    match.put((Object)"totalDistanceKm", (Object)routingResult.getTotalDistanceKm());
                    matchingResults.add((Object)match);
                }
                catch (MapApiException e) {
                    System.out.println("Skipping incompatible driver offer ID " + offer.getId() + " due to mapping error: " + e.getMessage());
                }
            }
            return ResponseEntity.ok((Object)matchingResults);
        }
        catch (Exception e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).body((Object)("Error processing matches: " + e.getMessage()));
        }
    }
}
