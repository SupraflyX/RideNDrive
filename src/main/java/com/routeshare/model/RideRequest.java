package com.routeshare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/**
 * RideRequest represents a request submitted by a passenger for a carpool match.
 *
 * Demonstrates:
 * - Domain Modeling: Formalizes passenger requirements as constraints for matching.
 * - Temporal Modeling: Specifying start and end bounds of a pickup time window.
 */
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "ride_requests")
public class RideRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Passenger is required")
    @ManyToOne(optional = false)
    @JoinColumn(name = "passenger_id", nullable = false)
    private User passenger;

    @ManyToOne
    @JoinColumn(name = "trip_offer_id")
    @JsonIgnore
    private TripOffer tripOffer;

    @NotBlank(message = "Origin cannot be empty")
    @Column(nullable = false)
    private String origin;

    @NotBlank(message = "Destination cannot be empty")
    @Column(nullable = false)
    private String destination;

    @FutureOrPresent(message = "Pickup time window start must be in the future")
    @NotNull(message = "Pickup time window start is required")
    @Column(nullable = false)
    private LocalDateTime pickupTimeWindowStart;

    @FutureOrPresent(message = "Pickup time window end must be in the future")
    @NotNull(message = "Pickup time window end is required")
    @Column(nullable = false)
    private LocalDateTime pickupTimeWindowEnd;

    public RideRequest() {
    }

    public RideRequest(User passenger, String origin, String destination, LocalDateTime pickupTimeWindowStart, LocalDateTime pickupTimeWindowEnd) {
        this.passenger = passenger;
        this.origin = origin;
        this.destination = destination;
        this.pickupTimeWindowStart = pickupTimeWindowStart;
        this.pickupTimeWindowEnd = pickupTimeWindowEnd;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getPassenger() {
        return passenger;
    }

    public void setPassenger(User passenger) {
        this.passenger = passenger;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public LocalDateTime getPickupTimeWindowStart() {
        return pickupTimeWindowStart;
    }

    public void setPickupTimeWindowStart(LocalDateTime pickupTimeWindowStart) {
        this.pickupTimeWindowStart = pickupTimeWindowStart;
    }

    public LocalDateTime getPickupTimeWindowEnd() {
        return pickupTimeWindowEnd;
    }

    public void setPickupTimeWindowEnd(LocalDateTime pickupTimeWindowEnd) {
        this.pickupTimeWindowEnd = pickupTimeWindowEnd;
    }

    public TripOffer getTripOffer() {
        return tripOffer;
    }

    public void setTripOffer(TripOffer tripOffer) {
        this.tripOffer = tripOffer;
    }
}
