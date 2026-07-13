package com.routeshare.repository;

import com.routeshare.model.RideRequest;
import com.routeshare.model.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * RideRequestRepository provides DB operations for the RideRequest entity.
 *
 * Demonstrates:
 * - Repository Pattern: Separating passenger request persistence.
 * - Derived queries: rating eligibility (FR-10) is answered by the database,
 *   not by in-memory filtering.
 */
@Repository
public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {

    /** Completed bookings of a passenger — used to derive rateable drivers. */
    List<RideRequest> findByPassengerIdAndStatus(Long passengerId, BookingStatus status);

    /** Completed bookings on a driver's trips — used to derive rateable passengers. */
    List<RideRequest> findByTripOfferDriverIdAndStatus(Long driverId, BookingStatus status);

    /** All requests submitted by a passenger (account deletion cascade). */
    List<RideRequest> findByPassengerId(Long passengerId);
}
