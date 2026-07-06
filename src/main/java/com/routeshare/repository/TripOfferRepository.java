package com.routeshare.repository;

import com.routeshare.model.TripOffer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripOfferRepository
extends JpaRepository<TripOffer, Long> {
    public List<TripOffer> findByDriverId(Long var1);
}
