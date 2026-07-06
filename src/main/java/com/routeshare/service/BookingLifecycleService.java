package com.routeshare.service;

import com.routeshare.model.RideRequest;
import com.routeshare.model.TripOffer;
import com.routeshare.model.User;
import com.routeshare.model.enums.BookingStatus;
import com.routeshare.model.enums.NotificationType;
import com.routeshare.repository.RideRequestRepository;
import com.routeshare.repository.TripOfferRepository;
import com.routeshare.service.NotificationService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingLifecycleService {
    private static final Map<BookingStatus, Set<BookingStatus>> LEGAL_TRANSITIONS = new EnumMap(BookingStatus.class);
    private final RideRequestRepository rideRequestRepository;
    private final TripOfferRepository tripOfferRepository;
    private final NotificationService notificationService;

    @Autowired
    public BookingLifecycleService(RideRequestRepository rideRequestRepository, TripOfferRepository tripOfferRepository, NotificationService notificationService) {
        this.rideRequestRepository = rideRequestRepository;
        this.tripOfferRepository = tripOfferRepository;
        this.notificationService = notificationService;
    }

    public boolean isLegalTransition(BookingStatus from, BookingStatus to) {
        return LEGAL_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(BookingStatus.class)).contains(to);
    }

    @Transactional
    public RideRequest confirm(Long rideRequestId, Long actorId) {
        this.requireDriverActor(rideRequestId, actorId, "confirm");
        RideRequest request = this.transition(rideRequestId, BookingStatus.CONFIRMED);
        this.notifyPassenger(request, "Your booking from " + request.getOrigin() + " to " + request.getDestination() + " was confirmed by the driver.");
        return request;
    }

    @Transactional
    public RideRequest reject(Long rideRequestId, Long actorId) {
        this.requireDriverActor(rideRequestId, actorId, "decline");
        RideRequest request = this.transition(rideRequestId, BookingStatus.REJECTED);
        this.notifyPassenger(request, "Your booking from " + request.getOrigin() + " to " + request.getDestination() + " was declined by the driver.");
        return request;
    }

    @Transactional
    public RideRequest cancel(Long rideRequestId, Long actorId) {
        RideRequest request = (RideRequest)this.rideRequestRepository.findById(rideRequestId).orElseThrow(() -> new RuntimeException("RideRequest not found with id: " + rideRequestId));
        if (request.getPassenger() == null || actorId == null || !request.getPassenger().getId().equals((Object)actorId)) {
            throw new SecurityException("Only the passenger who owns this booking may withdraw it.");
        }
        request = this.transition(rideRequestId, BookingStatus.CANCELLED);
        this.notifyDriver(request, "A passenger cancelled their booking (" + request.getOrigin() + " -> " + request.getDestination() + ").");
        return request;
    }

    private void requireDriverActor(Long rideRequestId, Long actorId, String action) {
        User tripDriver;
        RideRequest request = (RideRequest)this.rideRequestRepository.findById(rideRequestId).orElseThrow(() -> new RuntimeException("RideRequest not found with id: " + rideRequestId));
        User user = tripDriver = request.getTripOffer() == null ? null : request.getTripOffer().getDriver();
        if (tripDriver == null || actorId == null || !tripDriver.getId().equals((Object)actorId)) {
            throw new SecurityException("Only the driver of this trip may " + action + " the booking.");
        }
    }

    @Transactional
    public int completeTrip(Long tripOfferId, Long actorId) {
        TripOffer offer = (TripOffer)this.tripOfferRepository.findById(tripOfferId).orElseThrow(() -> new RuntimeException("TripOffer not found with id: " + tripOfferId));
        if (offer.getDriver() == null || actorId == null || !offer.getDriver().getId().equals((Object)actorId)) {
            throw new SecurityException("Only the driver of this trip may complete it.");
        }
        List<RideRequest> bookings = offer.getPassengers();
        int completed = 0;
        for (RideRequest request : bookings) {
            if (request.getStatus() != BookingStatus.CONFIRMED) continue;
            request.setStatus(BookingStatus.COMPLETED);
            this.rideRequestRepository.save(request);
            this.notifyPassenger(request, "Your trip to " + request.getDestination() + " is complete. Please rate your driver!");
            ++completed;
        }
        User driver = offer.getDriver();
        this.notificationService.notify(driver, NotificationType.BOOKING, "Trip " + offer.getOrigin() + " -> " + offer.getDestination() + " marked complete. " + completed + " passenger booking(s) closed.");
        return completed;
    }

    public List<User> rateableCounterparts(Long userId) {
        ArrayList counterparts = new ArrayList();
        HashSet seen = new HashSet();
        for (RideRequest r : this.rideRequestRepository.findByPassengerIdAndStatus(userId, BookingStatus.COMPLETED)) {
            User driver;
            if (r.getTripOffer() == null || r.getTripOffer().getDriver() == null || (driver = r.getTripOffer().getDriver()).getId().equals((Object)userId) || !seen.add((Object)driver.getId())) continue;
            counterparts.add((Object)driver);
        }
        for (RideRequest r : this.rideRequestRepository.findByTripOfferDriverIdAndStatus(userId, BookingStatus.COMPLETED)) {
            User passenger = r.getPassenger();
            if (passenger == null || passenger.getId().equals((Object)userId) || !seen.add((Object)passenger.getId())) continue;
            counterparts.add((Object)passenger);
        }
        return counterparts;
    }

    private RideRequest transition(Long rideRequestId, BookingStatus target) {
        RideRequest request = (RideRequest)this.rideRequestRepository.findById(rideRequestId).orElseThrow(() -> new RuntimeException("RideRequest not found with id: " + rideRequestId));
        BookingStatus current = request.getStatus();
        if (!this.isLegalTransition(current, target)) {
            throw new IllegalStateException("Illegal booking transition: " + String.valueOf((Object)((Object)current)) + " -> " + String.valueOf((Object)((Object)target)));
        }
        request.setStatus(target);
        return (RideRequest)this.rideRequestRepository.save(request);
    }

    private void notifyPassenger(RideRequest request, String message) {
        if (request.getPassenger() != null) {
            this.notificationService.notify(request.getPassenger(), NotificationType.BOOKING, message);
        }
    }

    private void notifyDriver(RideRequest request, String message) {
        if (request.getTripOffer() != null && request.getTripOffer().getDriver() != null) {
            this.notificationService.notify(request.getTripOffer().getDriver(), NotificationType.BOOKING, message);
        }
    }

    static {
        LEGAL_TRANSITIONS.put(BookingStatus.PENDING, EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.REJECTED, BookingStatus.CANCELLED));
        LEGAL_TRANSITIONS.put(BookingStatus.CONFIRMED, EnumSet.of(BookingStatus.CANCELLED, BookingStatus.COMPLETED));
        LEGAL_TRANSITIONS.put(BookingStatus.REJECTED, EnumSet.noneOf(BookingStatus.class));
        LEGAL_TRANSITIONS.put(BookingStatus.CANCELLED, EnumSet.noneOf(BookingStatus.class));
        LEGAL_TRANSITIONS.put(BookingStatus.COMPLETED, EnumSet.noneOf(BookingStatus.class));
    }
}
