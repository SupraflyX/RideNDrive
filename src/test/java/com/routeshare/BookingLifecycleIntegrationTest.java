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

/**
 * Integration test suite for Sprint 6 (Booking Lifecycle, FR-12) and
 * Sprint 7 (Notification Center, FR-13).
 *
 * Verifies:
 * - The booking finite state machine: legal transitions succeed, illegal
 *   transitions are rejected with HTTP 409 CONFLICT.
 * - Observer-pattern notifications: booking transitions and new ratings
 *   emit notifications to the correct recipient.
 * - Inbox endpoints: listing, unread count, mark-read, mark-all-read.
 */
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

    // ─────────────────────────────────────────────────────────────────
    // Setup: driver + passenger + trip + pending booking
    // ─────────────────────────────────────────────────────────────────

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
        RideRequest request = new RideRequest(passenger, "Messina Nord", "Catania Centro",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2));
        request = rideRequestRepository.save(request);
        pendingRequestId = request.getId();

        assertThat(request.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    // ─────────────────────────────────────────────────────────────────
    // FR-12: State machine — legal transitions
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    public void confirmPendingBooking_succeeds_andNotifiesPassenger() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/" + pendingRequestId + "/confirm", null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("status")).isEqualTo("CONFIRMED");

        // Observer check: passenger received a BOOKING notification
        ResponseEntity<List> inbox = restTemplate.getForEntity(
                "/api/notifications/user/" + passengerId, List.class);
        assertThat(inbox.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(inbox.getBody()).isNotEmpty();
    }

    @Test
    @Order(5)
    public void confirmAlreadyConfirmed_returnsConflict() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/" + pendingRequestId + "/confirm", null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().get("error").toString()).contains("Illegal booking transition");
    }

    @Test
    @Order(6)
    public void rejectConfirmedBooking_returnsConflict() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/" + pendingRequestId + "/reject", null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(7)
    public void cancelConfirmedBooking_succeeds() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/" + pendingRequestId + "/cancel", null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("status")).isEqualTo("CANCELLED");
    }

    @Test
    @Order(8)
    public void anyTransitionFromCancelled_returnsConflict() {
        ResponseEntity<Map> confirmAgain = restTemplate.postForEntity(
                "/api/bookings/" + pendingRequestId + "/confirm", null, Map.class);
        assertThat(confirmAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> cancelAgain = restTemplate.postForEntity(
                "/api/bookings/" + pendingRequestId + "/cancel", null, Map.class);
        assertThat(cancelAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(9)
    public void rejectPendingBooking_succeeds() {
        User passenger = userRepository.findById(Long.valueOf(passengerId)).orElseThrow();
        RideRequest request = new RideRequest(passenger, "Villafranca", "Taormina",
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2));
        secondRequestId = rideRequestRepository.save(request).getId();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/" + secondRequestId + "/reject", null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("status")).isEqualTo("REJECTED");
    }

    @Test
    @Order(10)
    public void transitionOnMissingBooking_returnsNotFound() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/999999/confirm", null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(11)
    public void stateMachine_guardTable_isFormallyCorrect() {
        // Direct unit-level verification of the transition relation
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

    // ─────────────────────────────────────────────────────────────────
    // FR-12: Trip completion
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(12)
    public void completeTrip_transitionsConfirmedBookings() {
        // Attach a CONFIRMED booking to the trip, then complete the trip
        User passenger = userRepository.findById(Long.valueOf(passengerId)).orElseThrow();
        TripOffer trip = tripOfferRepository.findById(Long.valueOf(tripId)).orElseThrow();

        RideRequest request = new RideRequest(passenger, "Messina Sud", "Catania Nord",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(3));
        request.setStatus(BookingStatus.CONFIRMED);
        request.setTripOffer(trip);
        rideRequestRepository.save(request);

        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/trip/" + tripId + "/complete", null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number completed = (Number) res.getBody().get("completedBookings");
        assertThat(completed.intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(13)
    public void completeMissingTrip_returnsNotFound() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/trip/888888/complete", null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────
    // FR-13: Notification inbox endpoints
    // ─────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────
    // FR-13: Rating event emits a notification (Observer)
    // ─────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────
    // NFR-2: Runtime config endpoint (no secrets hardcoded in frontend)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(19)
    public void configEndpoint_exposesMapsKeyField_withoutSecretsInFrontend() {
        ResponseEntity<Map> res = restTemplate.getForEntity("/api/config", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Field must exist; in the test profile the key is empty (env-driven, never hardcoded)
        assertThat(res.getBody()).containsKey("mapsApiKey");
    }

    @Test
    @Order(18)
   