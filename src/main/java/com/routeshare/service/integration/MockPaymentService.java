package com.routeshare.service.integration;

import org.springframework.stereotype.Service;

/**
 * MockPaymentService mocks Stripe API integration for identity and cash splitting logic.
 *
 * Demonstrates:
 * - Anticipation of Change (SE Principle 5): Abstraction allows easy substitution of real Stripe SDK.
 * - Testability: Avoids hitting network APIs during test executions.
 */
@Service
public class MockPaymentService implements PaymentService {

    @Override
    public boolean verifyIdentity(Long userId) {
        System.out.println("[COTS Integration] Stripe verification successful for user: " + userId);
        return true;
    }

    @Override
    public boolean processPayment(Long payerId, Long payeeId, double amount) {
        System.out.printf("[COTS Integration] Stripe transferred $%.2f from User %d to User %d%n", amount, payerId, payeeId);
        return true;
    }
}

