package com.routeshare.service;

import com.routeshare.model.*;
import com.routeshare.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

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
            // don't hash it twice if what we got already looks like a bcrypt hash
            String pwd = userDetails.getPassword();
            if (!pwd.startsWith("$2a$") && !pwd.startsWith("$2b$") && !pwd.startsWith("$2y$")) {
                pwd = org.mindrot.jbcrypt.BCrypt.hashpw(pwd, org.mindrot.jbcrypt.BCrypt.gensalt());
            }
            user.setPassword(pwd);
        }
        return userRepository.save(user);
    }

    // deleting an account has to clear everything pointing at it first, all in one
    // transaction so we never end up half deleted
    @Transactional
    public void delete(Long id) {
        paymentTransactionRepository.deleteAll(paymentTransactionRepository.findByPayerIdOrPayeeId(id, id));
        notificationRepository.deleteAll(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(id));
        ratingRepository.deleteAll(ratingRepository.findByReviewerIdOrRevieweeId(id, id));
        vehicleRepository.deleteAll(vehicleRepository.findByDriverId(id));
        tripOfferRepository.deleteAll(tripOfferRepository.findByDriverId(id));
        // has to be a bulk delete. TripOffer cascades PERSIST to its bookings, so removing
        // them one at a time lets a managed trip re-save them on flush and the final user
        // delete then fails on a foreign key. flush/clear afterwards keep things in sync
        rideRequestRepository.deleteBulkByPassengerId(id);
        userRepository.deleteById(id);
    }
}
