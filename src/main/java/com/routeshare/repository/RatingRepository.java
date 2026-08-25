package com.routeshare.repository;

import com.routeshare.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    List<Rating> findByRevieweeId(Long revieweeId);

    long countByRevieweeId(Long revieweeId);

    // both directions, needed when deleting an account
    List<Rating> findByReviewerIdOrRevieweeId(Long reviewerId, Long revieweeId);
}
