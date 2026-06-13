package com.routeshare.service;

import com.routeshare.model.RideRequest;
import com.routeshare.repository.RideRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * RideRequestService handles business logic and CRUD operations for RideRequests.
 *
 * Demonstrates:
 * - Layered Architecture: Decoupling passenger request operations.
 */
@Service
public class RideRequestService {

    private final RideRequestRepository rideRequestRepository;

    @Autowired
    public RideRequestService(RideRequestRepository rideRequestRepository) {
        this.rideRequestRepository = rideRequestRepository;
    }

    public List<RideRequest> findAll() {
        return rideRequestRepository.findAll();
    }

    public Optional<RideRequest> findById(Long id) {
        return rideRequestRepository.findById(id);
    }

    public RideRequest save(RideRequest rideRequest) {
        return rideRequestRepository.save(rideRequest);
    }

    public RideRequest update(Long id, RideRequest rideRequestDetails) {
        RideRequest rideRequest = rideRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("RideRequest not found with id: " + id));
        rideRequest.setOrigin(rideRequestDetails.getOrigin());
        rideRequest.setDestination(rideRequestDetails.getDestination());
        rideRequest.setPickupTimeWindowStart(rideRequestDetails.getPickupTimeWindowStart());
        rideRequest.setPickupTimeWindowEnd(rideRequestDetails.getPickupTimeWindowEnd());
        rideRequest.setPassenger(rideRequestDetails.getPassenger());
        return rideRequestRepository.save(rideRequest);
    }

    public void delete(Long id) {
        rideRequestRepository.deleteById(id);
    }
}
