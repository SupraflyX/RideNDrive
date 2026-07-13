package com.routeshare.repository;

import com.routeshare.model.TripOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * TripOfferRepository provides DB operations for the TripOffer entity.
 *
 * Demonstrates:
 * - Repository Pattern: Separates physical persistence details from business layer.
 * - Derived queries: a driver's offers are fetched by the database (account
 *   deletion cascade, policy ranking) instead of filtering findAll() in memory.
 */
@Repository
public interface TripOfferRepository extends JpaRepository<TripOffer, Long> {

    List<TripOffer> findByDriverId(Long driverId);
}
