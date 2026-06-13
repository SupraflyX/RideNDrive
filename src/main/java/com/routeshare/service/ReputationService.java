package com.routeshare.service;

import com.routeshare.model.Rating;
import com.routeshare.model.User;
import com.routeshare.model.enums.IncentiveTier;
import com.routeshare.repository.RatingRepository;
import com.routeshare.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ReputationService implements the event-driven 5-step reputation and incentive workflow.
 *
 * Demonstrates:
 * - Observer Pattern (GoF Behavioral / Ch. 9): Observes and reacts to new rating event submissions.
 * - Temporal Workflow Pattern: Integrates time-decay penalties based on user inactivity.
 * - Quality Attributes - Maintainability & Correctness (Ch. 2): Highly cohesive step boundaries with transactional isolation.
 */
@Service
public class ReputationService {

    private final UserRepository userRepository;
    private final RatingRepository ratingRepository;

    @Autowired
    public ReputationService(UserRepository userRepository, RatingRepository ratingRepository) {
        this.userRepository = userRepository;
        this.ratingRepository = ratingRepository;
    }

    /**
     * Handles rating submission events, orchestrating the 5-step reputation calculation workflow.
     *
     * @param revieweeId The database ID of the user who was rated.
     * @param newScore The score value (1-5) of the rating received.
     */
    @Transactional
    public void handleNewRating(Long revieweeId, int newScore) {
        // STEP 1: Event Reception (triggered by RatingService when a user is rated)
        User user = userRepository.findById(revieweeId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + revieweeId));

        // STEP 2: Rolling Average Recalculation
        List<Rating> ratings = ratingRepository.findByRevieweeId(revieweeId);
        double rollingAverage = calculateRollingAverage(ratings, newScore);

        // STEP 3: Time-Decay Check
        double finalScore = applyTimeDecayPenalty(user, rollingAverage);
        user.setReputationScore(finalScore);

        // STEP 4: Incentive Tier Mapping
        IncentiveTier newTier = mapScoreToTier(finalScore);
        user.setIncentiveTier(newTier);

        // STEP 5: Propagation (Update user profile, reset last active date to now, and persist)
        user.setLastActiveDate(LocalDateTime.now());
        userRepository.save(user);

        System.out.printf("[ReputationWorkflow] Processed rating for User %s. Avg: %.2f, Decay Score: %.2f, Tier: %s%n",
                user.getName(), rollingAverage, finalScore, newTier);
    }

    /**
     * Computes the mathematical rolling average including a newly received score.
     */
    private double calculateRollingAverage(List<Rating> ratings, int newScore) {
        double sum = newScore;
        for (Rating rating : ratings) {
            sum += rating.getScore();
        }
        // Total includes all past ratings + the new one
        return sum / (ratings.size() + 1);
    }

    /**
     * Inspects user inactivity duration and applies time-decay penalty if inactivity > 30 days.
     */
    public double applyTimeDecayPenalty(User user, double baseScore) {
        LocalDateTime lastActive = user.getLastActiveDate();
        if (lastActive == null) {
            return baseScore;
        }

        long daysInactive = Duration.between(lastActive, LocalDateTime.now()).toDays();
        if (daysInactive > 30) {
            // Apply 0.01 penalty point per day of inactivity beyond 30 days, capped at a maximum 1.0 point penalty.
            double penalty = 0.01 * (daysInactive - 30);
            if (penalty > 1.0) {
                penalty = 1.0;
            }
            return Math.max(0.0, baseScore - penalty);
        }
        return baseScore;
    }

    /**
     * Maps numerical reputation score onto IncentiveTier classification.
     */
    public IncentiveTier mapScoreToTier(double score) {
        if (score >= 4.8) {
            return IncentiveTier.PREMIUM_PRICING;
        } else if (score >= 4.5) {
            return IncentiveTier.GOLD;
        } else if (score >= 4.0) {
            return IncentiveTier.SILVER;
        } else {
            return IncentiveTier.STANDARD;
        }
    }
}
