package com.routeshare.repository;

import com.routeshare.model.Rating;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RatingRepository
extends JpaRepository<Rating, Long> {
    public List<Rating> findByRevieweeId(Long var1);

    public long countByRevieweeId(Long var1);

    public List<Rating> findByReviewerIdOrRevieweeId(Long var1, Long var2);
}
