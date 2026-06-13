package com.routeshare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

/**
 * Vehicle represents a vehicle registered by a DRIVER to perform carpool trips.
 *
 * Demonstrates:
 * - Domain Association (UML Class Diagram support / Ch. 7): A 1-to-many relationship where User (Driver) has Vehicles.
 * - Object-Oriented Integrity: Ensuring capacity is capped and validated.
 */
@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Driver is required")
    @ManyToOne(optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @Min(value = 1, message = "Capacity must be at least 1")
    @Max(value = 8, message = "Capacity cannot exceed 8")
    @Column(nullable = false)
    private int capacity;

    @NotBlank(message = "Make cannot be empty")
    @Column(nullable = false)
    private String make;

    @NotBlank(message = "Model cannot be empty")
    @Column(nullable = false)
    private String model;

    public Vehicle() {
    }

    public Vehicle(User driver, int capacity, String make, String model) {
        this.driver = driver;
        this.capacity = capacity;
        this.make = make;
        this.model = model;
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

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public String getMake() {
        return make;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
