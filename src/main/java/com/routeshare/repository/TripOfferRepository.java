package com.routeshare.repository;

import com.routeshare.model.TripOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripOfferRepository extends JpaRepository<TripOffer, Long> {

    List<TripOffer> findByDriverId(Long driverId);
}
