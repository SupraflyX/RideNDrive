package com.routeshare.service;

import com.routeshare.model.*;
import com.routeshare.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * UserService handles business logic and CRUD operations for Users.
 *
 * Demonstrates:
 * - Layered Architecture (MVC/Layered): Service layer separating business operations from Controllers and repositories.
 * - Dependency Injection (Spring IoC / Creational pattern): Interacting with repositories via interface abstractions.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final TripOfferRepository tripOfferRepository;
    private final RideRequestRepository rideRequestRepository;
    private final RatingRepository ratingRepository;
    private final NotificationRepository notificationRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    public UserService(UserRepository userRepository,
                       VehicleRepository vehicleRepository,
                       TripOfferRepository tripOfferRepository,
                       RideRequestRepository rideRequestRepository,
                       RatingRepository ratingRepository,
                       NotificationRepository notificationRepository,
                       PaymentTransactionRepository paymentTransactionRepository) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.tripOfferRepository = tripOfferRepository;
        this.rideRequestRepository = rideRequestRepository;
        this.ratingRepository = ratingRepository;
        this.notificationRepository = notificationRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public User update(Long id, User userDetails) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        user.setName(userDetails.getName());
        user.setRole(userDetails.getRole());
        user.setReputationScore(userDetails.getReputationScore());
        user.setIncentiveTier(userDetails.getIncentiveTier());
        user.setLastActiveDate(userDetails.getLastActiveDate());
        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            // Check if it's already a BCrypt hash (starts with $2a$ or $2b$ or $2y$) to avoid double hashing, 
            // though typically raw passwords shouldn't look like bcrypt hashes.
            String pwd = userDetails.getPassword();
            if (!pwd.startsWith("$2a$") && !pwd.startsWith("$2b$") && !pwd.startsWith("$2y$")) {
                pwd = org.mindrot.jbcrypt.BCrypt.hashpw(pwd, org.mindrot.jbcrypt.BCrypt.gensalt());
            }
            user.setPassword(pwd);
        }
        return userRepository.save(user);
    }

    /**
     * Cascading account deletion (FR-4): all dependent records are removed via
     * repository derived queries (answered by the database, not by loading
     * every row into memory) inside a single transaction.
     */
    @Transactional
    public void delete(Long id) {
        paymentTransactionRepository.deleteAll(paymentTransactionRepository.findByPayerIdOrPayeeId(id, id));
        notificationRepository.deleteAll(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(id));
        ratingRepository.deleteAll(ratingRepository.findByReviewerIdOrRevieweeId(id, id));
        vehicleRepository.deleteAll(vehicleRepository.findByDriverId(id));
        tripOfferRepository.deleteAll(tripOfferRepository.findByDriverId(id));
        // Detach the passenger's bookings from their trips first: TripOffer.passengers
        // has cascade=ALL, so a managed trip would re-persist ("resurrect") removed
        // bookings at flush and the final user delete would hit the FK constraint.
        List<RideRequest> bookings = rideRequestRepository.findByPassengerId(id);
        for (RideRequest booking : bookings) {
            if (booking.getTripOffer() != null) {
                booking.getTripOffer().getPassengers().remove(booking);
            }
        }
        rideRequestRepository.deleteAll(bookings);
        userRepository.deleteById(id);
    }
}
