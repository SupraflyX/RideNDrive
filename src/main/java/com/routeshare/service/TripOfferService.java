package com.routeshare.service;

import com.routeshare.model.TripOffer;
import com.routeshare.repository.TripOfferRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * TripOfferService handles business logic and CRUD operations for TripOffers.
 *
 * Demonstrates:
 * - Layered Architecture: Decoupling HTTP interactions from database queries.
 */
@Service
public class TripOfferService {

    private final TripOfferRepository tripOfferRepository;

    @Autowired
    public TripOfferService(TripOfferRepository tripOfferRepository) {
        this.tripOfferRepository = tripOfferRepository;
    }

    public List<TripOffer> findAll() {
        return tripOfferRepository.findAll();
    }

    public Optional<TripOffer> findById(Long id) {
        return tripOfferRepository.findById(id);
    }

    public TripOffer save(TripOffer tripOffer) {
        return tripOfferRepository.save(tripOffer);
    }

    public TripOffer update(Long id, TripOffer tripOfferDetails) {
        TripOffer tripOffer = tripOfferRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("TripOffer not found with id: " + id));
        tripOffer.setOrigin(tripOfferDetails.getOrigin());
        tripOffer.setDestination(tripOfferDetails.getDestination());
        tripOffer.setDepartureTime(tripOfferDetails.getDepartureTime());
        tripOffer.setMaxStops(tripOfferDetails.getMaxStops());
        tripOffer.setMaxDetourMinutes(tripOfferDetails.getMaxDetourMinutes());
        tripOffer.setDriver(tripOfferDetails.getDriver());
        return tripOfferRepository.save(tripOffer);
    }

    public void delete(Long id) {
        tripOfferRepository.deleteById(id);
    }
}
