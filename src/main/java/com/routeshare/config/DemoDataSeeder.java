package com.routeshare.config;

import com.routeshare.model.*;
import com.routeshare.model.enums.*;
import com.routeshare.repository.*;
import com.routeshare.service.PaymentLedgerService;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * DemoDataSeeder populates an EMPTY database with a living demo world so the
 * application never presents as a ghost town: personas with earned reputations,
 * upcoming and completed trips, bookings in every lifecycle state, ratings,
 * notifications, driver policies and payment history.
 *
 * Safety: runs only outside the test profile AND only when no users exist —
 * it can never touch real data or the CI suite. All demo accounts share the
 * password "demo123" (listed in the README) so any persona can be driven live
 * at the examination.
 */
@Component
@Profile("!test")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_PASSWORD = "demo123";

    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final TripOfferRepository trips;
    private final RideRequestRepository requests;
    private final RatingRepository ratings;
    private final NotificationRepository notifications;
    private final DriverTravelRuleRepository travelRules;
    private final DriverPricingRuleRepository pricingRules;
    private final PaymentLedgerService ledger;
    private final JdbcTemplate jdbc;

    public DemoDataSeeder(UserRepository users, VehicleRepository vehicles, TripOfferRepository trips,
                          RideRequestRepository requests, RatingRepository ratings,
                          NotificationRepository notifications, DriverTravelRuleRepository travelRules,
                          DriverPricingRuleRepository pricingRules, PaymentLedgerService ledger, JdbcTemplate jdbc) {
        this.users = users;
        this.vehicles = vehicles;
        this.trips = trips;
        this.requests = requests;
        this.ratings = ratings;
        this.notifications = notifications;
        this.travelRules = travelRules;
        this.pricingRules = pricingRules;
        this.ledger = ledger;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return; // never touch an existing database
        }
        log.info("Empty database detected — seeding demo world (password for all demo accounts: {})", DEMO_PASSWORD);

        String hash = BCrypt.hashpw(DEMO_PASSWORD, BCrypt.gensalt());

        // ── People ──────────────────────────────────────────────────
        User giulia = driver("Giulia", hash, 4.72, IncentiveTier.GOLD);
        User marco  = driver("Marco", hash, 4.15, IncentiveTier.SILVER);
        User piera  = driver("Piera", hash, 4.88, IncentiveTier.PREMIUM_PRICING);
        User alice  = passenger("Alice", hash, 4.91, IncentiveTier.PREMIUM_PRICING);
        User bruno  = passenger("Bruno", hash, 3.40, IncentiveTier.STANDARD);
        User chiara = passenger("Chiara", hash, 4.55, IncentiveTier.GOLD);
        User davide = passenger("Davide", hash, 5.00, IncentiveTier.STANDARD);

        vehicles.save(new Vehicle(giulia, 3, "Fiat", "500X"));
        vehicles.save(new Vehicle(marco, 4, "Volkswagen", "Golf"));
        vehicles.save(new Vehicle(piera, 2, "Mini", "Cooper"));

        // ── Driver policies (FR-15 / FR-16) ─────────────────────────
        travelRules.save(new DriverTravelRule(giulia, TravelRuleType.MIN_PASSENGER_REPUTATION, 4.0, 10));
        travelRules.save(new DriverTravelRule(giulia, TravelRuleType.NO_LARGE_LUGGAGE, null, 20));
        travelRules.save(new DriverTravelRule(piera, TravelRuleType.SAME_DESTINATION_ONLY, null, 10));
        pricingRules.save(new DriverPricingRule(giulia, PricingRuleType.BASE_RATE_PER_KM, 0.45, 10));
        pricingRules.save(new DriverPricingRule(giulia, PricingRuleType.RUSH_HOUR_SURCHARGE_PCT, 15, 20));
        pricingRules.save(new DriverPricingRule(giulia, PricingRuleType.LOYALTY_TIER_DISCOUNT_PCT, 10, 30));
        pricingRules.save(new DriverPricingRule(piera, PricingRuleType.BASE_RATE_PER_KM, 0.60, 10));
        pricingRules.save(new DriverPricingRule(piera, PricingRuleType.SAME_DESTINATION_DISCOUNT_PCT, 12, 20));

        // ── Upcoming trips ───────────────────────────────────────────
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).with(LocalTime.of(7, 45));
        TripOffer t1 = trips.save(new TripOffer(giulia, "Messina", "Catania", tomorrow, 3, 30));
        TripOffer t2 = trips.save(new TripOffer(marco, "Messina", "Taormina", tomorrow.plusMinutes(45), 2, 25));
        TripOffer t3 = trips.save(new TripOffer(piera, "Catania", "Siracusa", LocalDateTime.now().plusDays(2).with(LocalTime.of(17, 30)), 2, 20));
        trips.save(new TripOffer(marco, "Palermo", "Messina", LocalDateTime.now().plusDays(3).with(LocalTime.of(9, 0)), 3, 40));

        // Bookings in various lifecycle states
        RideRequest pendingOnT1 = booking(chiara, t1, "Messina", "Catania", tomorrow, BookingStatus.PENDING, LuggageSize.SMALL);
        RideRequest confirmedOnT1 = booking(alice, t1, "Messina Nord", "Catania", tomorrow, BookingStatus.CONFIRMED, LuggageSize.NONE);
        booking(davide, t2, "Messina", "Taormina", tomorrow.plusMinutes(45), BookingStatus.CONFIRMED, LuggageSize.NONE);
        booking(bruno, t3, "Catania", "Siracusa", t3.getDepartureTime(), BookingStatus.REJECTED, LuggageSize.LARGE);

        // Open, unassigned requests (ranked in Find Passengers)
        openRequest(davide, "Messina", "Catania", tomorrow, LuggageSize.NONE);
        openRequest(bruno, "Villafranca", "Catania", tomorrow, LuggageSize.SMALL);

        // ── A completed trip yesterday: history, payments, ratings ──
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1).with(LocalTime.of(8, 0));
        TripOffer done = new TripOffer(giulia, "Messina", "Catania", LocalDateTime.now().plusMinutes(5), 2, 30);
        done = trips.save(done);
        RideRequest doneAlice = completedBooking(alice, done, "Messina", "Catania");
        RideRequest doneChiara = completedBooking(chiara, done, "Messina Sud", "Catania");
        // Backdate the historical rows (bean validation guards API input, not curated history)
        jdbc.update("UPDATE trip_offers SET departure_time = ? WHERE id = ?", yesterday, done.getId());
        jdbc.update("UPDATE ride_requests SET pickup_time_window_start = ?, pickup_time_window_end = ? WHERE id = ?",
                yesterday.minusMinutes(30), yesterday.plusHours(2), doneAlice.getId());
        jdbc.update("UPDATE ride_requests SET pickup_time_window_start = ?, pickup_time_window_end = ? WHERE id = ?",
                yesterday.minusMinutes(30), yesterday.plusHours(2), doneChiara.getId());

        ledger.record(alice, giulia, 7.40, PaymentTransaction.Status.COMPLETED, "Messina → Catania", doneAlice.getId());
        ledger.record(chiara, giulia, 8.15, PaymentTransaction.Status.COMPLETED, "Messina Sud → Catania", doneChiara.getId());
        ledger.record(davide, marco, 6.30, PaymentTransaction.Status.COMPLETED, "Messina → Taormina", null);

        ratings.save(new Rating(alice, giulia, 5, "DRIVER_RATED"));
        ratings.save(new Rating(chiara, giulia, 4, "DRIVER_RATED"));
        ratings.save(new Rating(giulia, alice, 5, "PASSENGER_RATED"));
        ratings.save(new Rating(giulia, chiara, 5, "PASSENGER_RATED"));

        notifications.save(new Notification(giulia, NotificationType.BOOKING, "Chiara booked a seat on your trip Messina -> Catania (payment on hold)."));
        notifications.save(new Notification(alice, NotificationType.BOOKING, "Your booking from Messina Nord to Catania was confirmed by the driver."));
        notifications.save(new Notification(giulia, NotificationType.RATING, "You received a new 5-star rating."));
        notifications.save(new Notification(chiara, NotificationType.BOOKING, "Your trip to Catania is complete. Please rate your driver!"));

        log.info("Demo world seeded: 7 users, 3 vehicles, 5 trips, bookings in every state, payments, ratings, notifications, driver policies.");
    }

    // ── helpers ──────────────────────────────────────────────────────
    private User driver(String name, String hash, double reputation, IncentiveTier tier) {
        User u = new User(name, UserRole.DRIVER, hash);
        u.setReputationScore(reputation);
        u.setIncentiveTier(tier);
        return users.save(u);
    }

    private User passenger(String name, String hash, double reputation, IncentiveTier tier) {
        User u = new User(name, UserRole.PASSENGER, hash);
        u.setReputationScore(reputation);
        u.setIncentiveTier(tier);
        return users.save(u);
    }

    private RideRequest booking(User pax, TripOffer trip, String origin, String dest,
                                LocalDateTime around, BookingStatus status, LuggageSize luggage) {
        RideRequest r = new RideRequest(pax, origin, dest, around.minusMinutes(30), around.plusHours(2));
        r.setTripOffer(trip);
        r.setStatus(status);
        r.setLuggageSize(luggage);
        return requests.save(r);
    }

    private void openRequest(User pax, String origin, String dest, LocalDateTime around, LuggageSize luggage) {
        RideRequest r = new RideRequest(pax, origin, dest, around, around.plusHours(3));
        r.setLuggageSize(luggage);
        requests.save(r);
    }

    /** Persist validation-safe, then the caller backdates via JDBC. */
    private RideRequest completedBooking(User pax, TripOffer trip, String origin, String dest) {
        RideRequest r = new RideRequest(pax, origin, dest,
                LocalDateTime.now().plusMinutes(5), LocalDateTime.now().plusHours(2));
        r.setTripOffer(trip);
        r.setStatus(BookingStatus.COMPLETED);
        return requests.save(r);
    }
}
