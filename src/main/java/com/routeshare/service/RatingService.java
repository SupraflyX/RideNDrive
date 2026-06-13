package com.routeshare.service;

import com.routeshare.model.Rating;
import com.routeshare.repository.RatingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * RatingService handles business logic and CRUD operations for Ratings.
 *
 * Demonstrates:
 * - Event Trigger / Observer Pattern (GoF Behavioral / Ch. 9): Propagates rating submission events to the Reputation system.
 * - Separation of Concerns (SE Principle 2): Rating persistence is separated from reputation calculation.
 */
@Service
public class RatingService {

    private final RatingRepository ratingRepository;
    private final ReputationService reputationService;

    @Autowired
    public RatingService(RatingRepository ratingRepository, @Lazy ReputationService reputationService) {
        this.ratingRepository = ratingRepository;
        this.reputationService = reputationService;
    }

    public List<Rating> findAll() {
        return ratingRepository.findAll();
    }

    public Optional<Rating> findById(Long id) {
        return ratingRepository.findById(id);
    }

    public Rating save(Rating rating) {
        Rating savedRating = ratingRepository.save(rating);
        // Step 1: Event Reception propagation to Reputation system
        if (savedRating.getReviewee() != null) {
            reputationService.handleNewRating(savedRating.getReviewee().getId(), savedRating.getScore());
        }
        return savedRating;
    }

    public void delete(Long id) {
        ratingRepository.deleteById(id);
    }
}
