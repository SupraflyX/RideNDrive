package com.routeshare.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.routeshare.model.TripOffer;
import com.routeshare.model.User;
import com.routeshare.model.enums.BookingStatus;
import com.routeshare.model.enums.LuggageSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name="ride_requests")
public class RideRequest {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @NotNull(message="Passenger is required")
    @ManyToOne(optional=false)
    @JoinColumn(name="passenger_id", nullable=false)
    private @NotNull(message="Passenger is required") User passenger;
    @ManyToOne
    @JoinColumn(name="trip_offer_id")
    @JsonIgnore
    private TripOffer tripOffer;
    @NotBlank(message="Origin cannot be empty")
    @Column(nullable=false)
    private @NotBlank(message="Origin cannot be empty") String origin;
    @NotBlank(message="Destination cannot be empty")
    @Column(nullable=false)
    private @NotBlank(message="Destination cannot be empty") String destination;
    @FutureOrPresent(message="Pickup time window start must be in the future")
    @NotNull(message="Pickup time window start is required")
    @Column(nullable=false)
    private @FutureOrPresent(message="Pickup time window start must be in the future") @NotNull(message="Pickup time window start is required") LocalDateTime pickupTimeWindowStart;
    @FutureOrPresent(message="Pickup time window end must be in the future")
    @NotNull(message="Pickup time window end is required")
    @Column(nullable=false)
    private @FutureOrPresent(message="Pickup time window end must be in the future") @NotNull(message="Pickup time window end is required") LocalDateTime pickupTimeWindowEnd;
    @Enumerated(value=EnumType.STRING)
    @Column(nullable=false, columnDefinition="varchar(20) default 'PENDING'")
    private BookingStatus status = BookingStatus.PENDING;
    @Enumerated(value=EnumType.STRING)
    @Column(nullable=false, columnDefinition="varchar(10) default 'NONE'")
    private LuggageSize luggageSize = LuggageSize.NONE;

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
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getPassenger() {
        return this.passenger;
    }

    public void setPassenger(User passenger) {
        this.passenger = passenger;
    }

    public String getOrigin() {
        return this.origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return this.destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public LocalDateTime getPickupTimeWindowStart() {
        return this.pickupTimeWindowStart;
    }

    public void setPickupTimeWindowStart(LocalDateTime pickupTimeWindowStart) {
        this.pickupTimeWindowStart = pickupTimeWindowStart;
    }

    public LocalDateTime getPickupTimeWindowEnd() {
        return this.pickupTimeWindowEnd;
    }

    public void setPickupTimeWindowEnd(LocalDateTime pickupTimeWindowEnd) {
        this.pickupTimeWindowEnd = pickupTimeWindowEnd;
    }

    public BookingStatus getStatus() {
        return this.status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public LuggageSize getLuggageSize() {
        return this.luggageSize;
    }

    public void setLuggageSize(LuggageSize luggageSize) {
        this.luggageSize = luggageSize;
    }

    public TripOffer getTripOffer() {
        return this.tripOffer;
    }

    public void setTripOffer(TripOffer tripOffer) {
        this.tripOffer = tripOffer;
    }
}
