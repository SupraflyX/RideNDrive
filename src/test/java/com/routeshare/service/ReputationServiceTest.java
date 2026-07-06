package com.routeshare.service;

import com.routeshare.model.*;
import com.routeshare.model.enums.IncentiveTier;
import com.routeshare.model.enums.UserRole;
import com.routeshare.repository.RatingRepository;
import com.routeshare.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ReputationServiceTest validates the 5-step reputation and incentive tier logic.
 *
 * Demonstrates:
 * - V&V / Quality Correctness (Ch. 2): Confirms that rating events trigger rolling updates and tier progressions.
 * - Temporal Mocking: Checks time-decay behavior for users who are inactive for >30 days.
 */
public class ReputationServiceTest {

    private UserRepository userRepository;
    private RatingRepository ratingRepository;
    private ReputationService reputationService;

    private User testUser;
    private User reviewer;

    @BeforeEach
    public void setUp() {
        userRepository = mock(UserRepository.class);
        ratingRepository = mock(RatingRepository.class);
        reputationService = new ReputationService(userRepository, ratingRepository);

        testUser = new User("Bob", UserRole.PASSENGER);
        testUser.setId(2L);
        testUser.setReputationScore(4.0);
        testUser.setIncentiveTier(IncentiveTier.SILVER);
        testUser.setLastActiveDate(LocalDateTime.now());

        reviewer = new User("Alice", UserRole.DRIVER);
        reviewer.setId(1L);
    }

    @Test
    public void testRollingAverageCalculation() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser));

        // Existing ratings in DB: score 4 and score 5
        Rating r1 = new Rating(reviewer, testUser, 4, "DRIVER_RATED");
        Rating r2 = new Rating(reviewer, testUser, 5, "DRIVER_RATED");
        when(ratingRepository.findByRevieweeId(2L)).thenReturn(Arrays.asList(r1, r2));

        // Submit new rating of score 3
        // Recalculation: (4 + 5 + 3) / 3 = 4.0
        reputationService.handleNewRating(2L, 3);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals(4.0, savedUser.getReputationScore(), 0.001);
        assertEquals(IncentiveTier.SILVER, savedUser.getIncentiveTier());
    }

    @Test
    public void testTierUpgradeToGold() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser));

        // Bob has two existing high ratings (5, 5) and gets a new rating of 5.
        // Average: 5.0. This should map to PREMIUM_PRICING since score >= 4.8.
        Rating r1 = new Rating(reviewer, testUser, 5, "DRIVER_RATED");
        Rating r2 = new Rating(reviewer, testUser, 5, "DRIVER_RATED");
        when(ratingRepository.findByRevieweeId(2L)).thenReturn(Arrays.asList(r1, r2));

        reputationService.handleNewRating(2L, 5);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals(5.0, savedUser.getReputationScore(), 0.001);
        assertEquals(IncentiveTier.PREMIUM_PRICING, savedUser.getIncentiveTier());
    }

    @Test
    public void testTierDowngrade() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser));

        // Bob has bad ratings. Gets a new rating of 1.
        Rating r1 = new Rating(reviewer, testUser, 2, "DRIVER_RATED");
        when(ratingRepository.findByRevieweeId(2L)).thenReturn(Arrays.asList(r1));

        reputationService.handleNewRating(2L, 1);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        // Avg: (2 + 1) / 2 = 1.5. Maps to STANDARD tier.
        assertEquals(1.5, savedUser.getReputationScore(), 0.001);
        assertEquals(IncentiveTier.STANDARD, savedUser.getIncentiveTier());
    }

    @Test
    public void testTimeDecayPenalty() {
        // Set last active date to 45 days in the past
        testUser.setLastActiveDate(LocalDateTime.now().minusDays(45));

        // Apply decay penalty to rolling average of 4.5.
        // Inactivity = 45 days. 45 - 30 = 15 days past threshold.
        // Penalty: 15 * 0.01 = 0.15 points.
        // Expected: 4.5 - 0.15 = 4.35 (maps to SILVER, as 4.35 is in [4.0, 4.5))
        double decayedScore = reputationService.applyTimeDecayPenalty(testUser, 4.5);
        assertEquals(4.35, decayedScore, 0.001);
        assertEquals(IncentiveTier.SILVER, reputationService.mapScoreToTier(decayedScore));
    }

    @Test
    public void testNoDecayUnder30Days() {
        // Inactive for only 15 days
        testUser.setLastActiveDate(LocalDateTime.now().minusDays(15));

        double decayedScore = reputationService.applyTimeDecayPenalty(testUser, 4.5);
        assertEquals(4.5, decayedScore, 0.001);
        assertEquals(IncentiveTier.GOLD, reputationService.mapScoreToTier(decayedScore));
    }
}
