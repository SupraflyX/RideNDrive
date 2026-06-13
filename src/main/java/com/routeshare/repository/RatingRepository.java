package com.routeshare.repository;

import com.routeshare.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * RatingRepository provides DB operations for the Rating entity.
 *
 * Demonstrates:
 * - Repository Pattern: Custom query capabilities to compute aggregates (rolling average).
 * - Separation of Concerns: Decoupling rating queries from the user profile management.
 */
@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {
    List<Rating> findByRevieweeId(Long revieweeId);
    long countByRevieweeId(Long revieweeId);
}
