package com.routeshare;

import com.routeshare.model.Rating;
import com.routeshare.model.User;
import com.routeshare.model.enums.IncentiveTier;
import com.routeshare.model.enums.UserRole;
import com.routeshare.repository.UserRepository;
import com.routeshare.service.RatingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/* the reputation maths through the real service and a real database. the unit tests mock
   the rating repository, so they only see what the mock returns. this goes through
   RatingService for real, which is where the double-counting bug lived */
@SpringBootTest
@ActiveProfiles("test")
class ReputationIntegrationTest {

    @Autowired RatingService ratingService;
    @Autowired UserRepository userRepository;

    @Test
    void reputationIsTheMeanOfEveryRating() {
        User reviewer = userRepository.save(new User("RepReviewer", UserRole.DRIVER, "secret123"));
        User reviewee = userRepository.save(new User("RepReviewee", UserRole.PASSENGER, "secret123"));

        ratingService.save(new Rating(reviewer, reviewee, 5, "DRIVER_RATED"));
        ratingService.save(new Rating(reviewer, reviewee, 5, "DRIVER_RATED"));
        ratingService.save(new Rating(reviewer, reviewee, 1, "DRIVER_RATED"));

        /* (5 + 5 + 1) / 3 = 3.67, not 3.0 - which is what came out when the newest
           rating was counted twice */
        User after = userRepository.findById(reviewee.getId()).orElseThrow();
        assertThat(after.getReputationScore()).isCloseTo(11.0 / 3.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(after.getIncentiveTier()).isEqualTo(IncentiveTier.STANDARD);
    }

    @Test
    void oneFiveStarRatingGivesTheTopTier() {
        User reviewer = userRepository.save(new User("RepReviewer2", UserRole.DRIVER, "secret123"));
        User reviewee = userRepository.save(new User("RepReviewee2", UserRole.PASSENGER, "secret123"));

        ratingService.save(new Rating(reviewer, reviewee, 5, "PASSENGER_RATED"));

        User after = userRepository.findById(reviewee.getId()).orElseThrow();
        assertThat(after.getReputationScore()).isEqualTo(5.0);
        assertThat(after.getIncentiveTier()).isEqualTo(IncentiveTier.PREMIUM_PRICING);
    }
}
