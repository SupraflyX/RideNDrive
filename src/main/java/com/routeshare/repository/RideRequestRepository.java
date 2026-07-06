package com.routeshare.repository;

import com.routeshare.model.RideRequest;
import com.routeshare.model.enums.BookingStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RideRequestRepository
extends JpaRepository<RideRequest, Long> {
    public List<RideRequest> findByPassengerIdAndStatus(Long var1, BookingStatus var2);

    public List<RideRequest> findByTripOfferDriverIdAndStatus(Long var1, BookingStatus var2);

    public List<RideRequest> findByPassengerId(Long var1);
}
