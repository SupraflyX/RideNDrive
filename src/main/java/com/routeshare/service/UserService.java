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

    @Autowired
    public UserService(UserRepository userRepository,
                       VehicleRepository vehicleRepository,
                       TripOfferRepository tripOfferRepository,
                       RideRequestRepository rideRequestRepository,
                       RatingRepository ratingRepository) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.tripOfferRepository = tripOfferRepository;
        this.rideRequestRepository = rideRequestRepository;
        this.ratingRepository = ratingRepository;
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

    @Transactional
    public void delete(Long id) {
        // Delete ratings where reviewer_id = id or reviewee_id = id
        List<Rating> ratings = ratingRepository.findAll().stream()
                .filter(r -> r.getReviewer().getId().equals(id) || r.getReviewee().getId().equals(id))
                .toList();
        ratingRepository.deleteAll(ratings);

        // Delete vehicles where driver_id = id
        List<Vehicle> vehicles = vehicleRepository.findByDriverId(id);
        vehicleRepository.deleteAll(vehicles);

        // Delete trip offers where driver_id = id
        List<TripOffer> trips = tripOfferRepository.findAll().stream()
                .filter(t -> t.getDriver().getId().equals(id))
                .toList();
        tripOfferRepository.deleteAll(trips);

        // Delete ride requests where passenger_id = id
        List<RideRequest> rides = rideRequestRepository.findAll().stream()
                .filter(r -> r.getPassenger().getId().equals(id))
                .toList();
        rideRequestRepository.deleteAll(rides);

        userRepository.deleteById(id);
    }
}

