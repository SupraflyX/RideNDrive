package com.routeshare.repository;

import com.routeshare.model.RideRequest;
import com.routeshare.model.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {

    // a passenger's completed bookings, used to work out which drivers they can rate
    List<RideRequest> findByPassengerIdAndStatus(Long passengerId, BookingStatus status);

    // completed bookings on a driver's trips, same idea the other way round
    List<RideRequest> findByTripOfferDriverIdAndStatus(Long driverId, BookingStatus status);

    // needed when deleting an account
    List<RideRequest> findByPassengerId(Long passengerId);

    // requests a driver could still pick up: not attached to any trip yet, still PENDING,
    // and the pickup window hasn't passed. done in sql so we don't load every request
    List<RideRequest> findByTripOfferIsNullAndStatusAndPickupTimeWindowStartAfter(
            BookingStatus status, java.time.LocalDateTime notBefore);

    // one jpql statement instead of deleting entities one by one. going entity-by-entity
    // meant a managed TripOffer could re-save the booking on flush and the delete would fail.
    // this skips the persistence context, and flush/clear keep it in sync afterwards
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RideRequest r where r.passenger.id = :passengerId")
    int deleteBulkByPassengerId(Long passengerId);
}
