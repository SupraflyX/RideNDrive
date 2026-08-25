package com.routeshare.service;

import com.routeshare.model.Rating;
import com.routeshare.repository.RatingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

// saving a rating also kicks off the reputation update and tells the person they were rated
@Service
public class RatingService {

    private final RatingRepository ratingRepository;
    private final ReputationService reputationService;
    private final NotificationService notificationService;

    @Autowired
    public RatingService(RatingRepository ratingRepository,
                         @Lazy ReputationService reputationService,
                         NotificationService notificationService) {
        this.ratingRepository = ratingRepository;
        this.reputationService = reputationService;
        this.notificationService = notificationService;
    }

    public List<Rating> findAll() {
        return ratingRepository.findAll();
    }

    public Optional<Rating> findById(Long id) {
        return ratingRepository.findById(id);
    }

    public Rating save(Rating rating) {
        Rating savedRating = ratingRepository.save(rating);
        if (savedRating.getReviewee() != null) {
            reputationService.handleNewRating(savedRating.getReviewee().getId(), savedRating.getScore());
            notificationService.notify(savedRating.getReviewee(),
                    com.routeshare.model.enums.NotificationType.RATING,
                    "You received a new " + savedRating.getScore() + "-star rating.");
        }
        return savedRating;
    }

    public void delete(Long id) {
        ratingRepository.deleteById(id);
    }
}
