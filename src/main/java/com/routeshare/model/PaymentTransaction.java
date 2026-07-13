package com.routeshare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * PaymentTransaction — one row of the payment ledger (refines FR-8).
 *
 * The gateway (PaymentService) stays a simulated external boundary; this
 * ledger persists what it reports, giving every fare a durable, referenced
 * record: passengers see what they paid, drivers see what they earned, and
 * bookings carry a verifiable payment reference.
 */
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    public enum Status { COMPLETED, HELD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique human-readable reference, e.g. PAY-2026-4F8A2C. */
    @Column(nullable = false, unique = true, length = 24)
    private String reference;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "payee_id", nullable = false)
    private User payee;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.COMPLETED;

    /** Human context, e.g. "Messina → Catania". */
    @Column(length = 200)
    private String memo;

    /** The booking this payment belongs to (plain column, survives request deletion). */
    private Long rideRequestId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public PaymentTransaction() {
    }

    public PaymentTransaction(String reference, User payer, User payee, double amount,
                              Status status, String memo, Long rideRequestId) {
        this.reference = reference;
        this.payer = payer;
        this.payee = payee;
        this.amount = amount;
        this.status = status;
        this.memo = memo;
        this.rideRequestId = rideRequestId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public User getPayer() { return payer; }
    public void setPayer(User payer) { this.payer = payer; }
    public User getPayee() { return payee; }
    public void setPayee(User payee) { this.payee = payee; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public Long getRideRequestId() { return rideRequestId; }
    public void setRideRequestId(Long rideRequestId) { this.rideRequestId = rideRequestId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
