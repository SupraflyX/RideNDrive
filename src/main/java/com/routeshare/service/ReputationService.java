package com.routeshare.service;

import com.routeshare.model.Rating;
import com.routeshare.model.User;
import com.routeshare.model.enums.IncentiveTier;
import com.routeshare.repository.RatingRepository;
import com.routeshare.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/* recalculates someone's reputation when they get a new rating: average their scores,
   knock a bit off if they've been inactive for a while, then move them up or down a tier */
@Service
public class ReputationService {

    private static final Logger log = LoggerFactory.getLogger(ReputationService.class);

    private final UserRepository userRepository;
    private final RatingRepository ratingRepository;

    @Autowired
    public ReputationService(UserRepository userRepository, RatingRepository ratingRepository) {
        this.userRepository = userRepository;
        this.ratingRepository = ratingRepository;
    }

    @Transactional
    public void handleNewRating(Long revieweeId, int newScore) {
        User user = userRepository.findById(revieweeId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + revieweeId));

        List<Rating> ratings = ratingRepository.findByRevieweeId(revieweeId);
        double rollingAverage = calculateRollingAverage(ratings);

        double finalScore = applyTimeDecayPenalty(user, rollingAverage);
        user.setReputationScore(finalScore);

        IncentiveTier newTier = mapScoreToTier(finalScore);
        user.setIncentiveTier(newTier);

        user.setLastActiveDate(LocalDateTime.now());
        userRepository.save(user);

        log.info("[ReputationWorkflow] Processed rating for User {}. Avg: {}, Decay Score: {}, Tier: {}",
                user.getName(), String.format("%.2f", rollingAverage), String.format("%.2f", finalScore), newTier);
    }

    /* RatingService saves the new rating before calling us, so it is ALREADY in this
       list. averaging the list is the whole calculation - adding newScore on top of it
       counted the newest rating twice and dragged every score toward it */
    private double calculateRollingAverage(List<Rating> ratings) {
        if (ratings.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (Rating rating : ratings) {
            sum += rating.getScore();
        }
        return sum / ratings.size();
    }

    // nothing happens for the first 30 days idle, then 0.01 off per day, up to 1.0 max
    public double applyTimeDecayPenalty(User user, double baseScore) {
        LocalDateTime lastActive = user.getLastActiveDate();
        if (lastActive == null) {
            return baseScore;
        }

        long daysInactive = Duration.between(lastActive, LocalDateTime.now()).toDays();
        if (daysInactive > 30) {
            double penalty = 0.01 * (daysInactive - 30);
            if (penalty > 1.0) {
                penalty = 1.0;
            }
            return Math.max(0.0, baseScore - penalty);
        }
        return baseScore;
    }

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
