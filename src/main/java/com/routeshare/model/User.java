package com.routeshare.model;

import com.routeshare.model.enums.IncentiveTier;
import com.routeshare.model.enums.UserRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/**
 * User represents a participant in the RouteShare platform, acting as a DRIVER or PASSENGER.
 *
 * Demonstrates:
 * - Rigor & Formality (SE Principle 1): Formal definition of the User schema via JPA metadata mapping to SQL.
 * - Object-Oriented Encapsulation: Restricting direct access to state fields and exposing via getter/setter interfaces.
 * - Software Quality Attributes - Reusability & Maintainability (Ch. 2): Generic data representation.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name cannot be empty")
    @Size(min = 3, max = 50, message = "Name must be between 3 and 50 characters")
    @Column(nullable = false)
    private String name;

    @NotNull(message = "Role is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private double reputationScore = 5.0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncentiveTier incentiveTier = IncentiveTier.STANDARD;

    @Column(nullable = false)
    private LocalDateTime lastActiveDate = LocalDateTime.now();

    /**
     * BCrypt hash of the user's password. WRITE_ONLY access means clients can
     * send a password in requests, but the hash is never serialized into any
     * API response (prevents credential-hash disclosure).
     */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "Password cannot be empty")
    @Size(min = 6, message = "Password must be at least 6 characters")
    @Column(nullable = false)
    private String password = "password";

    public User() {
    }

    public User(String name, UserRole role) {
        this.name = name;
        this.role = role;
        this.password = "password";
        this.reputationScore = 5.0;
        this.incentiveTier = IncentiveTier.STANDARD;
        this.lastActiveDate = LocalDateTime.now();
    }

    public User(String name, UserRole role, String password) {
        this.name = name;
        this.role = role;
        this.password = password;
        this.reputationScore = 5.0;
        this.incentiveTier = IncentiveTier.STANDARD;
        this.lastActiveDate = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public double getReputationScore() {
        return reputationScore;
    }

    public void setReputationScore(double reputationScore) {
        this.reputationScore = reputationScore;
    }

    public IncentiveTier getIncentiveTier() {
        return incentiveTier;
    }

    public void setIncentiveTier(IncentiveTier incentiveTier) {
        this.incentiveTier = incentiveTier;
    }

    public LocalDateTime getLastActiveDate() {
        return lastActiveDate;
    }

    public void setLastActiveDate(LocalDateTime lastActiveDate) {
        this.lastActiveDate = lastActiveDate;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
