package com.routeshare.model;

import com.routeshare.model.enums.PricingRuleType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/**
 * DriverPricingRule — one rule of a driver's self-composed pricing policy (FR-16).
 *
 * Demonstrates:
 * - Rule/policy customizability (project guideline): the driver owns her fare
 *   policy — rates, surcharges, discounts and the loyalty incentive — instead
 *   of tuning parameters of a platform formula.
 * - Interpreter-style evaluation: PricingEngine walks the driver's ordered rule
 *   set inside the existing Chain of Responsibility flow.
 */
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

    /** The driver's own value: €/km, %, or flat € depending on the rule type. */
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
