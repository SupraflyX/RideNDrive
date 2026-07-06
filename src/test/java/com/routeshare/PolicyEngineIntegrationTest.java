package com.routeshare;

import com.routeshare.model.RideRequest;
import com.routeshare.model.User;
import com.routeshare.model.enums.IncentiveTier;
import com.routeshare.model.enums.LuggageSize;
import com.routeshare.repository.RideRequestRepository;
import com.routeshare.repository.TripOfferRepository;
import com.routeshare.repository.UserRepository;
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
 * Integration suite for Sprint 9 — the driver-owned policy engine:
 *
 * - FR-15 travel policy rules: CRUD, veto evaluation on booking, candidate ranking
 * - FR-16 driver-composed pricing rules incl. the loyalty (incentive) discount
 * - Overbooking defect regression: a booking must be feasible TOGETHER with
 *   all already-active bookings, not in isolation
 * - Lifecycle authorization: only the right actor may drive a transition
 * - Monitoring endpoint exposure (DevOps practice area)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "google.maps.api-key=")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PolicyEngineIntegrationTest {

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private TripOfferRepository tripOfferRepository;
    @Autowired private RideRequestRepository rideRequestRepository;

    private static Integer driverId;        // main driver (capacity 3), owns rules
    private static Integer smallCarDriverId; // capacity 1 (overbooking regression)
    private static Integer paxGoodId;       // reputation 5.0, GOLD tier later
    private static Integer paxWeakId;       // reputation lowered to 3.2
    private static Integer tripId;          // main trip Messina -> Catania
    private static Integer smallTripId;     // small car trip
    private static Integer defaultDriverId;  // no rules at all (default pricing)
    private static Integer defaultTripId;
    private static Long minRepRuleId;

    private static final String ORIGIN = "Messina";
    private static final String DEST = "Catania";

    private Map<String, String> bookPayload(Integer passengerId, String origin, String destination, String luggage) {
        Map<String, String> p = new HashMap<>();
        p.put("origin", origin);
        p.put("destination", destination);
        p.put("passengerId", passengerId.toString());
        if (luggage != null) p.put("luggageSize", luggage);
        return p;
    }

    // ─────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    public void setup_usersAndTrips() {
        Map<String, String> d = new HashMap<>();
        d.put("name", "PolicyDriver"); d.put("password", "password123");
        d.put("make", "VW"); d.put("model", "Golf"); d.put("capacity", "3");
        driverId = (Integer) restTemplate.postForEntity("/api/auth/register-driver", d, Map.class).getBody().get("id");

        Map<String, String> d2 = new HashMap<>();
        d2.put("name", "SmallCarDriver"); d2.put("password", "password123");
        d2.put("make", "Smart"); d2.put("model", "ForTwo"); d2.put("capacity", "1");
        smallCarDriverId = (Integer) restTemplate.postForEntity("/api/auth/register-driver", d2, Map.class).getBody().get("id");

        Map<String, String> d3 = new HashMap<>();
        d3.put("name", "DefaultDriver"); d3.put("password", "password123");
        d3.put("make", "Opel"); d3.put("model", "Corsa"); d3.put("capacity", "2");
        defaultDriverId = (Integer) restTemplate.postForEntity("/api/auth/register-driver", d3, Map.class).getBody().get("id");

        Map<String, String> p1 = new HashMap<>();
        p1.put("name", "GoodPassenger"); p1.put("password", "password123");
        paxGoodId = (Integer) restTemplate.postForEntity("/api/auth/register-passenger", p1, Map.class).getBody().get("id");

        Map<String, String> p2 = new HashMap<>();
        p2.put("name", "WeakPassenger"); p2.put("password", "password123");
        paxWeakId = (Integer) restTemplate.postForEntity("/api/auth/register-passenger", p2, Map.class).getBody().get("id");

        // Weak passenger's reputation drops below a typical threshold
        User weak = userRepository.findById(Long.valueOf(paxWeakId)).orElseThrow();
        weak.setReputationScore(3.2);
        userRepository.save(weak);

        assertThat(driverId).isNotNull();
        assertThat(smallCarDriverId).isNotNull();
    }

    @Test
    @Order(2)
    public void setup_trips() {
        Map<String, Object> t = new HashMap<>();
        t.put("driver", Map.of("id", driverId));
        t.put("origin", ORIGIN);
        t.put("destination", DEST);
        t.put("departureTime", LocalDateTime.now().plusDays(1).toString());
        t.put("maxStops", 3);
        t.put("maxDetourMinutes", 45);
        tripId = (Integer) restTemplate.postForEntity("/api/trips", t, Map.class).getBody().get("id");

        Map<String, Object> t2 = new HashMap<>();
        t2.put("driver", Map.of("id", smallCarDriverId));
        t2.put("origin", ORIGIN);
        t2.put("destination", DEST);
        t2.put("departureTime", LocalDateTime.now().plusDays(2).toString());
        t2.put("maxStops", 3);
        // Detour budget 5 < minimum fallback leg time (6): sequential double-serving
        // of a 1-seat car is deterministically infeasible — capacity is the binding constraint
        t2.put("maxDetourMinutes", 5);
        smallTripId = (Integer) restTemplate.postForEntity("/api/trips", t2, Map.class).getBody().get("id");

        Map<String, Object> t3 = new HashMap<>();
        t3.put("driver", Map.of("id", defaultDriverId));
        t3.put("origin", ORIGIN);
        t3.put("destination", DEST);
        t3.put("departureTime", LocalDateTime.now().plusDays(3).toString());
        t3.put("maxStops", 2);
        t3.put("maxDetourMinutes", 45);
        defaultTripId = (Integer) restTemplate.postForEntity("/api/trips", t3, Map.class).getBody().get("id");

        assertThat(tripId).isNotNull();
        assertThat(smallTripId).isNotNull();
        assertThat(defaultTripId).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────────
    // FR-15: travel rule CRUD
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    public void addTravelRules_succeeds() {
        ResponseEntity<Map> r1 = restTemplate.postForEntity("/api/policies/travel/" + driverId,
                Map.of("type", "MIN_PASSENGER_REPUTATION", "numericValue", "4.0"), Map.class);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);
        minRepRuleId = ((Number) r1.getBody().get("id")).longValue();

        ResponseEntity<Map> r2 = restTemplate.postForEntity("/api/policies/travel/" + driverId,
                Map.of("type", "SAME_DESTINATION_ONLY"), Map.class);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> r3 = restTemplate.postForEntity("/api/policies/travel/" + driverId,
                Map.of("type", "NO_LARGE_LUGGAGE"), Map.class);
        assertThat(r3.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(4)
    public void listTravelRules_returnsAllThree() {
        ResponseEntity<List> res = restTemplate.getForEntity("/api/policies/travel/" + driverId, List.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).hasSize(3);
    }

    @Test
    @Order(5)
    public void addTravelRule_unknownType_isRejected() {
        ResponseEntity<Map> res = restTemplate.postForEntity("/api/policies/travel/" + driverId,
                Map.of("type", "ALWAYS_SUNNY"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(6)
    public void addMinReputationRule_withoutThreshold_isRejected() {
        ResponseEntity<Map> res = restTemplate.postForEntity("/api/policies/travel/" + driverId,
                Map.of("type", "MIN_PASSENGER_REPUTATION"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────
    // FR-15: veto evaluation on booking
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    public void booking_belowReputationThreshold_isVetoed() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/trips/" + tripId + "/book-passenger",
                bookPayload(paxWeakId, ORIGIN, DEST, "NONE"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody().get("violations").toString()).contains("MIN_PASSENGER_REPUTATION");
    }

    @Test
    @Order(8)
    public void booking_differentDestination_isVetoed() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/trips/" + tripId + "/book-passenger",
                bookPayload(paxGoodId, ORIGIN, "Palermo", "NONE"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody().get("violations").toString()).contains("SAME_DESTINATION_ONLY");
    }

    @Test
    @Order(9)
    public void booking_withLargeLuggage_isVetoed() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/trips/" + tripId + "/book-passenger",
                bookPayload(paxGoodId, ORIGIN, DEST, "LARGE"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody().get("violations").toString()).contains("NO_LARGE_LUGGAGE");
    }

    @Test
    @Order(10)
    public void booking_compliantCandidate_succeeds() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/trips/" + tripId + "/book-passenger",
                bookPayload(paxGoodId, ORIGIN, DEST, "SMALL"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("bookingStatus").toString()).isIn("SUCCESSFUL", "PAYMENT_HOLD");
    }

    @Test
    @Order(11)
    public void deleteTravelRule_reopensAccess() {
        restTemplate.delete("/api/policies/travel/rule/" + minRepRuleId);
        ResponseEntity<List> res = restTemplate.getForEntity("/api/policies/travel/" + driverId, List.class);
        assertThat(res.getBody()).hasSize(2);
    }

    // ─────────────────────────────────────────────────────────────────
    // Overbooking regression (Sprint 9 defect fix)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(12)
    public void overbooking_secondPassengerOnOneSeatCar_failsRouting() {
        // First passenger books the only seat (identical route: zero detour)
        ResponseEntity<Map> first = restTemplate.postForEntity(
                "/api/trips/" + smallTripId + "/book-passenger",
                bookPayload(paxGoodId, ORIGIN, DEST, "NONE"), Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("bookingStatus").toString()).isIn("SUCCESSFUL", "PAYMENT_HOLD");

        // Second passenger cannot fit: planning now includes BOTH bookings
        ResponseEntity<Map> second = restTemplate.postForEntity(
                "/api/trips/" + smallTripId + "/book-passenger",
                bookPayload(paxWeakId, ORIGIN, DEST, "NONE"), Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("bookingStatus")).isEqualTo("FAILED_ROUTING");
    }

    // ─────────────────────────────────────────────────────────────────
    // FR-15: candidate ranking
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(13)
    public void ranking_ordersHigherReputationFirst() {
        // Two open, unassigned requests with identical routes
        User good = userRepository.findById(Long.valueOf(paxGoodId)).orElseThrow();
        User weak = userRepository.findById(Long.valueOf(paxWeakId)).orElseThrow();

        RideRequest rGood = new RideRequest(good, ORIGIN, DEST,
                LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(3).plusHours(2));
        rGood.setLuggageSize(LuggageSize.NONE);
        rideRequestRepository.save(rGood);

        RideRequest rWeak = new RideRequest(weak, ORIGIN, DEST,
                LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(3).plusHours(2));
        rWeak.setLuggageSize(LuggageSize.SMALL);
        rideRequestRepository.save(rWeak);

        // Rank against the small-car driver (no travel rules -> nobody vetoed)
        ResponseEntity<List> res = restTemplate.getForEntity(
                "/api/policies/rank/" + smallCarDriverId + "/" + smallTripId, List.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> ranked = res.getBody();
        assertThat(ranked.size()).isGreaterThanOrEqualTo(2);

        double firstScore = ((Number) ranked.get(0).get("score")).doubleValue();
        double lastScore = ((Number) ranked.get(ranked.size() - 1).get("score")).doubleValue();
        assertThat(firstScore).isGreaterThanOrEqualTo(lastScore);

        Map<String, Object> firstReq = (Map<String, Object>) ranked.get(0).get("request");
        Map<String, Object> firstPax = (Map<String, Object>) firstReq.get("passenger");
        assertThat(firstPax.get("id")).isEqualTo(paxGoodId);
    }

    @Test
    @Order(14)
    public void ranking_forWrongDriverTripPair_is404() {
        ResponseEntity<Map> res = restTemplate.getForEntity(
                "/api/policies/rank/" + driverId + "/" + smallTripId, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────
    // FR-16: driver-composed pricing rules + loyalty incentive
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(15)
    public void addPricingRules_succeeds() {
        ResponseEntity<Map> r1 = restTemplate.postForEntity("/api/policies/pricing/" + driverId,
                Map.of("type", "BASE_RATE_PER_KM", "value", "1.00"), Map.class);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> r2 = restTemplate.postForEntity("/api/policies/pricing/" + driverId,
                Map.of("type", "LOYALTY_TIER_DISCOUNT_PCT", "value", "10"), Map.class);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> bad = restTemplate.postForEntity("/api/policies/pricing/" + driverId,
                Map.of("type", "FREE_RIDES_FOREVER", "value", "1"), Map.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(16)
    public void booking_usesDriverPricingRules_andLoyaltyDiscountForGoldRider() {
        // Promote the good passenger to GOLD (the incentive mechanism)
        User good = userRepository.findById(Long.valueOf(paxGoodId)).orElseThrow();
        good.setIncentiveTier(IncentiveTier.GOLD);
        userRepository.save(good);

        // Book on the main trip via a fresh compliant request (previous one from
        // Order 10 is already attached; use different pickup window to avoid duplicate rule)
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/trips/" + tripId + "/book-passenger",
                bookPayload(paxWeakId, ORIGIN, DEST, "NONE"), Map.class);
        // Weak passenger is no longer blocked (min-reputation rule was deleted in Order 11)
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Loyalty check happens for the GOLD rider: verify audit on a search quote instead
        Map<String, String> search = new HashMap<>();
        search.put("origin", ORIGIN);
        search.put("destination", DEST);
        search.put("date", LocalDateTime.now().plusDays(1).toLocalDate().toString());
        search.put("passengerId", paxGoodId.toString());
        ResponseEntity<List> quotes = restTemplate.postForEntity("/api/trips/search-matches", search, List.class);
        assertThat(quotes.getStatusCode()).isEqualTo(HttpStatus.OK);

        boolean sawDriverRules = false;
        for (Object o : quotes.getBody()) {
            Map<String, Object> match = (Map<String, Object>) o;
            if (!match.get("tripOfferId").equals(tripId)) continue;
            Map<String, Object> pricing = (Map<String, Object>) match.get("pricing");
            String applied = pricing.get("appliedPolicies").toString();
            assertThat(applied).contains("DriverRule:BASE_RATE_PER_KM");
            assertThat(applied).contains("DriverRule:LOYALTY_TIER_DISCOUNT");
            sawDriverRules = true;
        }
        assertThat(sawDriverRules).as("main trip must appear in search results with driver-rule pricing").isTrue();
    }

    @Test
    @Order(17)
    public void booking_withoutDriverRules_usesDefaultChain() {
        Map<String, String> search = new HashMap<>();
        search.put("origin", ORIGIN);
        search.put("destination", DEST);
        search.put("date", LocalDateTime.now().plusDays(3).toLocalDate().toString());
        search.put("passengerId", paxGoodId.toString());
        ResponseEntity<List> quotes = restTemplate.postForEntity("/api/trips/search-matches", search, List.class);
        assertThat(quotes.getStatusCode()).isEqualTo(HttpStatus.OK);

        boolean sawDefaultTrip = false;
        for (Object o : quotes.getBody()) {
            Map<String, Object> match = (Map<String, Object>) o;
            if (!match.get("tripOfferId").equals(defaultTripId)) continue;
            Map<String, Object> pricing = (Map<String, Object>) match.get("pricing");
            assertThat(pricing.get("appliedPolicies").toString()).doesNotContain("DriverRule:");
            sawDefaultTrip = true;
        }
        assertThat(sawDefaultTrip).as("rule-less trip must be priced by the default chain").isTrue();
    }

    // ─────────────────────────────────────────────────────────────────
    // Lifecycle authorization (Sprint 9 hardening)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(18)
    public void confirm_byNonDriver_isForbidden() {
        // The compliant booking from Order 10 belongs to driverId's trip
        List<RideRequest> paxRequests = rideRequestRepository.findByPassengerId(Long.valueOf(paxGoodId));
        RideRequest onMainTrip = paxRequests.stream()
                .filter(r -> r.getTripOffer() != null && r.getTripOffer().getId().equals(Long.valueOf(tripId)))
                .findFirst().orElseThrow();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/" + onMainTrip.getId() + "/confirm?actorId=" + paxGoodId, null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(19)
    public void cancel_byNonOwner_isForbidden() {
        List<RideRequest> paxRequests = rideRequestRepository.findByPassengerId(Long.valueOf(paxGoodId));
        RideRequest onMainTrip = paxRequests.stream()
                .filter(r -> r.getTripOffer() != null && r.getTripOffer().getId().equals(Long.valueOf(tripId)))
                .findFirst().orElseThrow();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/bookings/" + onMainTrip.getId() + "/cancel?actorId=" + driverId, null, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─────────────────────────────────────────────────────────────────
    // Monitoring (DevOps practice area)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(20)
    public void actuatorHealth_isExposed() {
        ResponseEntity<Map> res = restTemplate.getForEntity("/actuator/health", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("status")).isEqualTo("UP");
    }
}
