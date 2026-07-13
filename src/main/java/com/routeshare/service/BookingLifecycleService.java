package com.routeshare.service;

import com.routeshare.model.RideRequest;
import com.routeshare.model.TripOffer;
import com.routeshare.model.User;
import com.routeshare.model.enums.BookingStatus;
import com.routeshare.model.enums.NotificationType;
import com.routeshare.repository.RideRequestRepository;
import com.routeshare.repository.TripOfferRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BookingLifecycleService enforces the booking finite state machine and emits
 * notifications on every transition.
 *
 * Legal transitions:
 *   PENDING   -> CONFIRMED (driver accepts) | REJECTED (driver declines) | CANCELLED (passenger withdraws)
 *   CONFIRMED -> CANCELLED (passenger withdraws) | COMPLETED (trip finished)
 *   REJECTED / CANCELLED / COMPLETED -> terminal
 *
 * Authorization (Sprint 9 hardening): confirm/decline/complete may only be
 * performed by the trip's driver; withdraw only by the booking's owner —
 * violations surface as HTTP 403 via SecurityException.
 *
 * Demonstrates:
 * - State Pattern (GoF Behavioral / Ch. 9): The legal transition relation is an
 *   explicit, formally-defined guard table instead of scattered if-statements.
 * - Observer Pattern: Each successful transition publishes a notification event.
 * - Robustness (Quality Attribute, Ch. 2): Illegal transitions raise a checked,
 *   descriptive IllegalStateException surfaced as HTTP 409 by the controller.
 */
@Service
public class BookingLifecycleService {

    /** Formal transition relation of the booking state machine. */
    private static final Map<BookingStatus, Set<BookingStatus>> LEGAL_TRANSITIONS = new EnumMap<>(BookingStatus.class);

    static {
        LEGAL_TRANSITIONS.put(BookingStatus.PENDING,
                EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.REJECTED, BookingStatus.CANCELLED));
        LEGAL_TRANSITIONS.put(BookingStatus.CONFIRMED,
                EnumSet.of(BookingStatus.CANCELLED, BookingStatus.COMPLETED));
        LEGAL_TRANSITIONS.put(BookingStatus.REJECTED, EnumSet.noneOf(BookingStatus.class));
        LEGAL_TRANSITIONS.put(BookingStatus.CANCELLED, EnumSet.noneOf(BookingStatus.class));
        LEGAL_TRANSITIONS.put(BookingStatus.COMPLETED, EnumSet.noneOf(BookingStatus.class));
    }

    private final RideRequestRepository rideRequestRepository;
    private final TripOfferRepository tripOfferRepository;
    private final NotificationService notificationService;

    @Autowired
    public BookingLifecycleService(RideRequestRepository rideRequestRepository,
                                   TripOfferRepository tripOfferRepository,
                                   NotificationService notificationService) {
        this.rideRequestRepository = rideRequestRepository;
        this.tripOfferRepository = tripOfferRepository;
        this.notificationService = notificationService;
    }

    /** Returns true if the transition from -> to is allowed by the state machine. */
    public boolean isLegalTransition(BookingStatus from, BookingStatus to) {
        return LEGAL_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(BookingStatus.class)).contains(to);
    }

    /** Driver accepts a pending booking. Only the trip's driver may confirm. */
    @Transactional
    public RideRequest confirm(Long rideRequestId, Long actorId) {
        requireDriverActor(rideRequestId, actorId, "confirm");
        RideRequest request = transition(rideRequestId, BookingStatus.CONFIRMED);
        notifyPassenger(request, "Your booking from " + request.getOrigin() + " to "
                + request.getDestination() + " was confirmed by the driver.");
        return request;
    }

    /** Driver declines a pending booking. Only the trip's driver may decline. */
    @Transactional
    public RideRequest reject(Long rideRequestId, Long actorId) {
        requireDriverActor(rideRequestId, actorId, "decline");
        RideRequest request = transition(rideRequestId, BookingStatus.REJECTED);
        notifyPassenger(request, "Your booking from " + request.getOrigin() + " to "
                + request.getDestination() + " was declined by the driver.");
        return request;
    }

    /** Passenger withdraws a pending or confirmed booking. Only its owner may. */
    @Transactional
    public RideRequest cancel(Long rideRequestId, Long actorId) {
        RideRequest request = rideRequestRepository.findById(rideRequestId)
                .orElseThrow(() -> new RuntimeException("RideRequest not found with id: " + rideRequestId));
        if (request.getPassenger() == null || actorId == null
                || !request.getPassenger().getId().equals(actorId)) {
            throw new SecurityException("Only the passenger who owns this booking may withdraw it.");
        }
        request = transition(rideRequestId, BookingStatus.CANCELLED);
        notifyDriver(request, "A passenger cancelled their booking ("
                + request.getOrigin() + " -> " + request.getDestination() + ").");
        return request;
    }

    /**
     * Driver marks a whole trip as completed: every CONFIRMED booking on the
     * trip transitions to COMPLETED and each passenger is notified and invited
     * to rate the driver.
     *
     * @return the number of bookings transitioned to COMPLETED
     */
    @Transactional
    public int completeTrip(Long tripOfferId, Long actorId) {
        TripOffer offer = tripOfferRepository.findById(tripOfferId)
                .orElseThrow(() -> new RuntimeException("TripOffer not found with id: " + tripOfferId));
        if (offer.getDriver() == null || actorId == null || !offer.getDriver().getId().equals(actorId)) {
            throw new SecurityException("Only the driver of this trip may complete it.");
        }

        List<RideRequest> bookings = offer.getPassengers();
        int completed = 0;
        for (RideRequest request : bookings) {
            if (request.getStatus() == BookingStatus.CONFIRMED) {
                request.setStatus(BookingStatus.COMPLETED);
                rideRequestRepository.save(request);
                notifyPassenger(request, "Your trip to " + request.getDestination()
                        + " is complete. Please rate your driver!");
                completed++;
            }
        }

        User driver = offer.getDriver();
        notificationService.notify(driver, NotificationType.BOOKING,
                "Trip " + offer.getOrigin() + " -> " + offer.getDestination()
                        + " marked complete. " + completed + " passenger booking(s) closed.");
        return completed;
    }

    /**
     * Users this person is allowed to rate: only counterparts from COMPLETED
     * bookings (FR-10 integrity — you can rate only people you actually rode with).
     * Covers both directions: drivers of completed trips (as passenger) and
     * passengers of completed bookings (as driver).
     */
    public List<User> rateableCounterparts(Long userId) {
        List<User> counterparts = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        // As passenger: drivers of trips where this user's booking is COMPLETED
        for (RideRequest r : rideRequestRepository.findByPassengerIdAndStatus(userId, BookingStatus.COMPLETED)) {
            if (r.getTripOffer() != null && r.getTripOffer().getDriver() != null) {
                User driver = r.getTripOffer().getDriver();
                if (!driver.getId().equals(userId) && seen.add(driver.getId())) {
                    counterparts.add(driver);
                }
            }
        }

        // As driver: passengers whose bookings on this user's trips are COMPLETED
        for (RideRequest r : rideRequestRepository.findByTripOfferDriverIdAndStatus(userId, BookingStatus.COMPLETED)) {
            User passenger = r.getPassenger();
            if (passenger != null && !passenger.getId().equals(userId) && seen.add(passenger.getId())) {
                counterparts.add(passenger);
            }
        }
        return counterparts;
    }

    /**
     * Core guarded transition. Throws IllegalStateException when the requested
     * transition violates the state machine.
     */
    private RideRequest transition(Long rideRequestId, BookingStatus target) {
        RideRequest request = rideRequestRepository.findById(rideRequestId)
                .orElseThrow(() -> new RuntimeException("RideRequest not found with id: " + rideRequestId));

        BookingStatus current = request.getStatus();
        if (!isLegalTransition(current, target)) {
            throw new IllegalStateException("Illegal booking transition: " + current + " -> " + target);
        }

        request.setStatus(target);
        return rideRequestRepository.save(request);
    }

    /** Sprint 9 hardening: only the trip's driver may act on its bookings (403 otherwise). */
    private void requireDriverActor(Long rideRequestId, Long actorId, String action) {
        RideRequest request = rideRequestRepository.findById(rideRequestId)
                .orElseThrow(() -> new RuntimeException("RideRequest not found with id: " + rideRequestId));
        User tripDriver = (request.getTripOffer() == null) ? null : request.getTripOffer().getDriver();
        if (tripDriver == null || actorId == null || !tripDriver.getId().equals(actorId)) {
            throw new SecurityException("Only the driver of this trip may " + action + " the booking.");
        }
    }

    private void notifyPassenger(RideRequest request, String message) {
        if (request.getPassenger() != null) {
            notificationService.notify(request.getPassenger(), NotificationType.BOOKING, message);
        }
    }

    private void notifyDriver(RideRequest request, String message) {
        if (request.getTripOffer() != null && request.getTripOffer().getDriver() != null) {
            notificationService.notify(request.getTripOffer().getDriver(), NotificationType.BOOKING, message);
        }
    }
}
