package com.routeshare.model;

import com.routeshare.model.enums.TravelRuleType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

// one rule in a driver's own travel policy. TravelPolicyService is what checks them
@Entity
@Table(name = "driver_travel_rules")
public class DriverTravelRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Driver is required")
    @ManyToOne(optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @NotNull(message = "Rule type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TravelRuleType type;

    // only used by rules that need a number, e.g. MIN_PASSENGER_REPUTATION = 4.0
    private Double numericValue;

    @Column(nullable = false)
    private boolean enabled = true;

    // lower runs first
    @Column(nullable = false)
    private int priority = 10;

    public DriverTravelRule() {
    }

    public DriverTravelRule(User driver, TravelRuleType type, Double numericValue, int priority) {
        this.driver = driver;
        this.type = type;
        this.numericValue = numericValue;
        this.priority = priority;
        this.enabled = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getDriver() { return driver; }
    public void setDriver(User driver) { this.driver = driver; }
    public TravelRuleType getType() { return type; }
    public void setType(TravelRuleType type) { this.type = type; }
    public Double getNumericValue() { return numericValue; }
    public void setNumericValue(Double numericValue) { this.numericValue = numericValue; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}
