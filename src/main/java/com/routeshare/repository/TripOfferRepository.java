package com.routeshare.repository;

import com.routeshare.model.TripOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * TripOfferRepository provides DB operations for the TripOffer entity.
 *
 * Demonstrates:
 * - Repository Pattern: Separates physical persistence details from business layer.
 */
@Repository
public interface TripOfferRepository extends JpaRepository<TripOffer, Long> {
}
