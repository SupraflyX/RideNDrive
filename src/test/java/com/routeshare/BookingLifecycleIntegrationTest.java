package com.routeshare;

import com.routeshare.model.RideRequest;
import com.routeshare.model.TripOffer;
import com.routeshare.model.User;
import com.routeshare.model.enums.BookingStatus;
import com.routeshare.repository.RideRequestRepository;
import com.routeshare.repository.TripOfferRepository;
import com.routeshare.repository.UserRepository;
import com.routeshare.service.BookingLifecycleService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// end to end tests for the booking lifecycle and the notification inbox.
// checks that legal moves work, illegal ones come back 409, the right person gets
// notified each time, and the inbox endpoints behave
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BookingLifecycleIntegrationTest {

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private TripOfferRepository tripOfferRepository;

    @Autowired
    private BookingLifecycleService bookingLifecycleService;

    private static Integer driverId;
    private static Integer passengerId;
    private static Integer tripId;
    private static Long pendingRequestId;
    private static Long secondRequestId;

    // setup: driver, passenger, trip, one pending booking

    @Test
    @Order(1)
    public void setup_registerDriverAndPassenger() {
        Map<String, String> driver = new HashMap<>();
        driver.put("name", "LifecycleDriver");
        driver.put("password", "password123");
        driver.put("make", "Fiat");
        driver.put("model", "Panda");
        driver.put("capacity", "3");
        ResponseEntity<Map> dRes = restTemplate.postForEntity("/api/auth/register-driver", driver, Map.class);
        assertThat(dRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        driverId = (Integer) dRes.getBody().get("id");

        Map<String, String> pax = new HashMap<>();
        pax.put("name", "LifecyclePassenger");
        pax.put("password", "password123");
        ResponseEntity<Map> pRes = restTemplate.postForEntity("/api/auth/register-passenger", pax, Map.class);
        assertThat(pRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        passengerId = (Integer) pRes.getBody().get("id");
    }

    @Test
    @Order(2)
    public void setup_createTripOffer() {
        Map<String, Object> trip = new HashMap<>();
        Map<String, Object> driverRef = new HashMap<>();
        driverRef.put("id", driverId);
        trip.put("driver", driverRef);
        trip.put("origin", "Messina");
        trip.put("destination", "Catania");
        trip.put("departureTime", LocalDateTime.now().plusDays(1).toString());
        trip.put("maxStops", 3);
        trip.put("maxDetourMinutes", 45);

        ResponseEntity<Map> res = restTemplate.postForEntity("/api/trips", trip, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        tripId = (Integer) res.getBody().get("id");
        assertThat(tripId).isNotNull();
    }

    @Test
    @Order(3)
    public void newRideRequest_defaultsToPending() {
        User passenger = userRepository.findById(Long.valueOf(passengerId)).orElseThrow();
        TripOffer trip = tripOfferRepository.findById(Long.valueOf(tripId)).orElseThrow();
        RideRequest request = new RideRequest(passenger, "Messina Nord", "Catania Centro",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2));
        request.setTripOffer(trip); // lifecycle actions are authorized against the trip's driver
        request = rideRequestRepository.save(request);
        pendingRequestId = request.getId();

        assertThat(request.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    // legal transitions

    @Test
    @Order(4)
    public void confirmPendingBooking_succeeds_andNotifiesPassenger() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/" + pendingRequestId + "/confirm?actorId=" + driverId, null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("status")).isEqualTo("CONFIRMED");

        // the passenger should have been notified
        ResponseEntity<List> inbox = restTemplate.getForEntity(
                "/api/notifications/user/" + passengerId, List.class);
        assertThat(inbox.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(inbox.getBody()).isNotEmpty();
    }

    @Test
    @Order(5)
    public void confirmAlreadyConfirmed_returnsConflict() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/" + pendingRequestId + "/confirm?actorId=" + driverId, null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().get("error").toString()).contains("Illegal booking transition");
    }

    @Test
    @Order(6)
    public void rejectConfirmedBooking_returnsConflict() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/" + pendingRequestId + "/reject?actorId=" + driverId, null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(7)
    public void cancelConfirmedBooking_succeeds() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/" + pendingRequestId + "/cancel?actorId=" + passengerId, null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("status")).isEqualTo("CANCELLED");
    }

    @Test
    @Order(8)
    public void anyTransitionFromCancelled_returnsConflict() {
        ResponseEntity<Map> confirmAgain = restTemplate.postForEntity(
                "/api/bookings/" + pendingRequestId + "/confirm?actorId=" + driverId, null, Map.class);
        assertThat(confirmAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> cancelAgain = restTemplate.postForEntity(
                "/api/bookings/" + pendingRequestId + "/cancel?actorId=" + passengerId, null, Map.class);
        assertThat(cancelAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(9)
    public void rejectPendingBooking_succeeds() {
        User passenger = userRepository.findById(Long.valueOf(passengerId)).orElseThrow();
        TripOffer trip = tripOfferRepository.findById(Long.valueOf(tripId)).orElseThrow();
        RideRequest request = new RideRequest(passenger, "Villafranca", "Taormina",
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2));
        request.setTripOffer(trip);
        secondRequestId = rideRequestRepository.save(request).getId();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/" + secondRequestId + "/reject?actorId=" + driverId, null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("status")).isEqualTo("REJECTED");
    }

    @Test
    @Order(10)
    public void transitionOnMissingBooking_returnsNotFound() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/999999/confirm?actorId=" + driverId, null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(11)
    public void stateMachine_guardTable_isFormallyCorrect() {
        // check the transition table directly too
        assertThat(bookingLifecycleService.isLegalTransition(BookingStatus.PENDING, BookingStatus.CONFIRMED)).isTrue();
        assertThat(bookingLifecycleService.isLegalTransition(BookingStatus.PENDING, BookingStatus.REJECTED)).isTrue();
        assertThat(bookingLifecycleService.isLegalTransition(BookingStatus.PENDING, BookingStatus.CANCELLED)).isTrue();
        assertThat(bookingLifecycleService.isLegalTransition(BookingStatus.CONFIRMED, BookingStatus.COMPLETED)).isTrue();
        assertThat(bookingLifecycleService.isLegalTransition(BookingStatus.CONFIRMED, BookingStatus.CANCELLED)).isTrue();

        assertThat(bookingLifecycleService.isLegalTransition(BookingStatus.PENDING, BookingStatus.COMPLETED)).isFalse();
        assertThat(bookingLifecycleService.isLegalTransition(BookingStatus.REJECTED, BookingStatus.CONFIRMED)).isFalse();
        assertThat(bookingLifecycleService.isLegalTransition(BookingStatus.CANCELLED, BookingStatus.PENDING)).isFalse();
        assertThat(bookingLifecycleService.isLegalTransition(BookingStatus.COMPLETED, BookingStatus.CANCELLED)).isFalse();
    }

    // completing a trip

    @Test
    @Order(12)
    public void completeTrip_transitionsConfirmedBookings() {
        // put a confirmed booking on the trip, then close the trip
        User passenger = userRepository.findById(Long.valueOf(passengerId)).orElseThrow();
        TripOffer trip = tripOfferRepository.findById(Long.valueOf(tripId)).orElseThrow();

        RideRequest request = new RideRequest(passenger, "Messina Sud", "Catania Nord",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(3));
        request.setStatus(BookingStatus.CONFIRMED);
        request.setTripOffer(trip);
        rideRequestRepository.save(request);

        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/trip/" + tripId + "/complete?actorId=" + driverId, null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number completed = (Number) res.getBody().get("completedBookings");
        assertThat(completed.intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(13)
    public void completeMissingTrip_returnsNotFound() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/trip/888888/complete?actorId=" + driverId, null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // the inbox endpoints

    @Test
    @Order(14)
    public void unreadCount_reflectsEmittedNotifications() {
        ResponseEntity<Map> res = restTemplate.getForEntity(
                "/api/notifications/user/" + passengerId + "/unread-count", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number unread = (Number) res.getBody().get("unread");
        assertThat(unread.longValue()).isGreaterThan(0);
    }

    @Test
    @Order(15)
    public void markSingleNotificationRead_succeeds() {
        ResponseEntity<List> inbox = restTemplate.getForEntity(
                "/api/notifications/user/" + passengerId, List.class);
        Map<String, Object> first = (Map<String, Object>) inbox.getBody().get(0);
        Integer notifId = (Integer) first.get("id");

        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/notifications/" + notifId + "/mark-read", null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("read")).isEqualTo(true);
    }

    @Test
    @Order(16)
    public void markReadOnMissingNotification_returnsNotFound() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/notifications/777777/mark-read", null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(17)
    public void markAllRead_clearsUnreadCount() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/notifications/user/" + passengerId + "/mark-all-read", null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> count = restTemplate.getForEntity(
                "/api/notifications/user/" + passengerId + "/unread-count", Map.class);
        Number unread = (Number) count.getBody().get("unread");
        assertThat(unread.longValue()).isZero();
    }

    // you can only rate people you actually rode with

    @Test
    @Order(20)
    public void rateable_passengerCanRateDriver_afterCompletedTrip() {
        // the earlier test completed a booking between these two
        ResponseEntity<List> res = restTemplate.getForEntity(
                "/api/bookings/rateable/" + passengerId, List.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> counterparts = res.getBody();
        assertThat(counterparts).extracting(u -> u.get("id")).contains(driverId);
    }

    @Test
    @Order(21)
    public void rateable_driverCanRatePassenger_afterCompletedTrip() {
        ResponseEntity<List> res = restTemplate.getForEntity(
                "/api/bookings/rateable/" + driverId, List.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> counterparts = res.getBody();
        assertThat(counterparts).extracting(u -> u.get("id")).contains(passengerId);
    }

    @Test
    @Order(22)
    public void completeTrip_afterPickupWindowElapsed_succeeds() {
        // regression: completing a trip used to blow up with "could not commit JPA
        // transaction" because @FutureOrPresent on the pickup window was re-checked on
        // flush when the status changed. that rule only applies at creation now, so
        // transitions on old bookings have to keep working
        User driver = userRepository.save(
                new User("WindowElapsedDriver", com.routeshare.model.enums.UserRole.DRIVER));
        User pax = userRepository.save(
                new User("WindowElapsedPax", com.routeshare.model.enums.UserRole.PASSENGER));
        TripOffer trip = tripOfferRepository.save(
                new TripOffer(driver, "Milazzo", "Patti", LocalDateTime.now().plusHours(1), 2, 30));

        RideRequest booking = new RideRequest(pax, "Milazzo", "Patti",
                LocalDateTime.now().minusHours(13), LocalDateTime.now().minusHours(1)); // window fully elapsed
        booking.setTripOffer(trip);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking = rideRequestRepository.save(booking); // must not trip flush-time validation

        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/trip/" + trip.getId() + "/complete?actorId=" + driver.getId(), null, Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) res.getBody().get("completedBookings")).intValue()).isEqualTo(1);
        assertThat(rideRequestRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.COMPLETED);
    }
}
