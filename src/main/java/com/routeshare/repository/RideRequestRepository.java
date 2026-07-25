package com.routeshare.repository;

import com.routeshare.model.RideRequest;
import com.routeshare.model.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * Open candidates for a driver's trip (FR-15 ranking): requests that are not yet
     * attached to any trip, still PENDING, and whose pickup window has not elapsed.
     * Answered by the database rather than by loading every request into memory.
     */
    List<RideRequest> findByTripOfferIsNullAndStatusAndPickupTimeWindowStartAfter(
            BookingStatus status, java.time.LocalDateTime notBefore);

    /**
     * Bulk-deletes a passenger's bookings with a single JPQL statement.
     * Unlike entity-by-entity removal this bypasses the persistence context, so a
     * managed TripOffer (whose passengers collection cascades PERSIST) can never
     * "resurrect" a removed booking at flush — the root cause of a deletion defect
     * caught by PaymentAndRouteIntegrationTest. flush/clear keep the context honest.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RideRequest r where r.passenger.id = :passengerId")
    int deleteBulkByPassengerId(Long passengerId);
}
