package com.routeshare;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration suite for the payment ledger and the driver route cockpit
 * (refinements of FR-8 and FR-5/FR-7):
 *
 * - every booking produces a persisted, referenced ledger transaction
 * - both parties see the transfer on their statement
 * - account deletion cascades over ledger rows (regression: FK integrity)
 * - the route endpoint re-plans side-effect-free with metrics and waypoints
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "google.maps.api-key=")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PaymentAndRouteIntegrationTest {

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Autowired private TestRestTemplate restTemplate;

    private static Integer driverId;
    private static Integer paxId;
    private static Integer tripId;
    private static String paymentReference;

    private static final String ORIGIN = "Milazzo";
    private static final String DEST = "Cefalu";

    @Test
    @Order(1)
    public void setup_driverPassengerTrip() {
        Map<String, String> d = new HashMap<>();
        d.put("name", "LedgerDriver"); d.put("password", "password123");
        d.put("make", "Seat"); d.put("model", "Ibiza"); d.put("capacity", "3");
        driverId = (Integer) restTemplate.postForEntity("/api/auth/register-driver", d, Map.class).getBody().get("id");

        Map<String, String> p = new HashMap<>();
        p.put("name", "LedgerPassenger"); p.put("password", "password123");
        paxId = (Integer) restTemplate.postForEntity("/api/auth/register-passenger", p, Map.class).getBody().get("id");

        Map<String, Object> t = new HashMap<>();
        t.put("driver", Map.of("id", driverId));
        t.put("origin", ORIGIN);
        t.put("destination", DEST);
        t.put("departureTime", LocalDateTime.now().plusDays(1).toString());
        t.put("maxStops", 2);
        t.put("maxDetourMinutes", 40);
        tripId = (Integer) restTemplate.postForEntity("/api/trips", t, Map.class).getBody().get("id");

        assertThat(tripId).isNotNull();
    }

    @Test
    @Order(2)
    public void booking_createsReferencedLedgerTransaction() {
        Map<String, String> book = new HashMap<>();
        book.put("origin", ORIGIN);
        book.put("destination", DEST);
        book.put("passengerId", paxId.toString());
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/trips/" + tripId + "/book-passenger", book, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        // The booking response carries the receipt
        List<Map<String, Object>> pax = (List<Map<String, Object>>) res.getBody().get("passengers");
        Map<String, Object> payment = (Map<String, Object>) pax.get(0).get("payment");
        assertThat(payment).isNotNull();
        paymentReference = (String) payment.get("reference");
        assertThat(paymentReference).startsWith("PAY-");
        assertThat(((Number) payment.get("amount")).doubleValue()).isGreaterThan(0.0);
        assertThat(payment.get("status").toString()).isIn("COMPLETED", "HELD");
    }

    @Test
    @Order(3)
    public void bothParties_seeTheTransferOnTheirStatement() {
        ResponseEntity<List> payerSide = restTemplate.getForEntity("/api/payments/user/" + paxId, List.class);
        ResponseEntity<List> payeeSide = restTemplate.getForEntity("/api/payments/user/" + driverId, List.class);
        assertThat(payerSide.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(payeeSide.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(payerSide.getBody().toString()).contains(paymentReference);
        assertThat(payeeSide.getBody().toString()).contains(paymentReference);
    }

    @Test
    @Order(4)
    public void routeEndpoint_replansWithMetricsAndWaypoints() {
        ResponseEntity<Map> res = restTemplate.getForEntity("/api/trips/" + tripId + "/route", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> routing = (Map<String, Object>) res.getBody().get("routing");
        assertThat(Boolean.TRUE).isEqualTo(routing.get("feasible"));

        List<String> waypoints = (List<String>) res.getBody().get("waypoints");
        // origin + pickup + dropoff + destination for the single active booking
        assertThat(waypoints.size()).isGreaterThanOrEqualTo(4);
        assertThat(waypoints.get(0)).isEqualTo(ORIGIN);
        assertThat(waypoints.get(waypoints.size() - 1)).isEqualTo(DEST);
        assertThat(((Number) res.getBody().get("activeBookings")).intValue()).isEqualTo(1);
        assertThat(((Number) res.getBody().get("detourMinutes")).intValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(5)
    public void routeEndpoint_unknownTrip_is404() {
        ResponseEntity<Map> res = restTemplate.getForEntity("/api/trips/424242/route", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(6)
    public void accountDeletion_cascadesOverLedgerRows() {
        // Deleting the payer must remove the transaction (FK integrity regression)
        restTemplate.delete("/api/users/" + paxId);
        ResponseEntity<List> payeeSide = restTemplate.getForEntity("/api/payments/user/" + driverId, List.class);
        assertThat(payeeSide.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(payeeSide.getBody().toString()).doesNotContain(paymentReference);
    }
}
