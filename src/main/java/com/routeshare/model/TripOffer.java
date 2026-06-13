package com.routeshare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/**
 * TripOffer represents a carpooling trip posted by a driver, including constraints on routing.
 *
 * Demonstrates:
 * - Separation of Concerns (SE Principle 2): Separating TripOffer constraints from Passenger requests.
 * - Algorithmic Parameterization: Providing `maxStops` and `maxDetourMinutes` to parameterize the Search Algorithm.
 */
@Entity
@Table(name = "trip_offers")
public class TripOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Driver is required")
    @ManyToOne(optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @NotBlank(message = "Origin cannot be empty")
    @Column(nullable = false)
    private String origin;

    @NotBlank(message = "Destination cannot be empty")
    @Column(nullable = false)
    private String destination;

    @FutureOrPresent(message = "Departure time must be in the future")
    @NotNull(message = "Departure time is required")
    @Column(nullable = false)
    private LocalDateTime departureTime;

    @Min(value = 0, message = "Max stops cannot be negative")
    @Column(nullable = false)
    private int maxStops;

    @Min(value = 0, message = "Max detour minutes cannot be negative")
    @Column(nullable = false)
    private int maxDetourMinutes;

    @OneToMany(mappedBy = "tripOffer", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private java.util.List<RideRequest> passengers = new java.util.ArrayList<>();

    public TripOffer() {
    }

    public TripOffer(User driver, String origin, String destination, LocalDateTime departureTime, int maxStops, int maxDetourMinutes) {
        this.driver = driver;
        this.origin = origin;
        this.destination = destination;
        this.departureTime = departureTime;
        this.maxStops = maxStops;
        this.maxDetourMinutes = maxDetourMinutes;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getDriver() {
        return driver;
    }

    public void setDriver(User driver) {
        this.driver = driver;
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

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalDateTime departureTime) {
        this.departureTime = departureTime;
    }

    public int getMaxStops() {
        return maxStops;
    }

    public void setMaxStops(int maxStops) {
        this.maxStops = maxStops;
    }

    public int getMaxDetourMinutes() {
        return maxDetourMinutes;
    }

    public void setMaxDetourMinutes(int maxDetourMinutes) {
        this.maxDetourMinutes = maxDetourMinutes;
    }

    public java.util.List<RideRequest> getPassengers() {
        return passengers;
    }

    public void setPassengers(java.util.List<RideRequest> passengers) {
        this.passengers = passengers;
    }
}
