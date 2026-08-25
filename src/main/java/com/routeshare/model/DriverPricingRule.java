package com.routeshare.model;

import com.routeshare.model.enums.PricingRuleType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

// one rule in a driver's own pricing policy
@Entity
@Table(name = "driver_pricing_rules")
public class DriverPricingRule {

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
    private PricingRuleType type;

    // what this means depends on the rule type: eur/km, a percentage, or a flat eur amount
    @Column(name = "rule_value", nullable = false)
    private double value;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private int priority = 10;

    public DriverPricingRule() {
    }

    public DriverPricingRule(User driver, PricingRuleType type, double value, int priority) {
        this.driver = driver;
        this.type = type;
        this.value = value;
        this.priority = priority;
        this.enabled = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getDriver() { return driver; }
    public void setDriver(User driver) { this.driver = driver; }
    public PricingRuleType getType() { return type; }
    public void setType(PricingRuleType type) { this.type = type; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}
