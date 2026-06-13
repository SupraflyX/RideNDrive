package com.routeshare.repository;

import com.routeshare.model.RideRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * RideRequestRepository provides DB operations for the RideRequest entity.
 *
 * Demonstrates:
 * - Repository Pattern: Separating passenger request persistence.
 */
@Repository
public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {
}
