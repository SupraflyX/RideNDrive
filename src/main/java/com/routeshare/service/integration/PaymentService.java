package com.routeshare.service.integration;


/**
 * PaymentService defines a requires-interface for user verification and monetary transaction processing.
 *
 * Demonstrates:
 * - CBSE Boundary Specification: Provides clean interfaces for external banking integrations (Stripe).
 * - Modularity (SE Principle 3): High cohesion around transactional logic.
 */
public interface PaymentService {
    boolean verifyIdentity(Long userId);
    boolean processPayment(Long payerId, Long payeeId, double amount);
}
