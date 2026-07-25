package com.routeshare.service.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MockPaymentService.class);

    @Override
    public boolean verifyIdentity(Long userId) {
        log.info("[COTS Integration] Stripe verification successful for user: {}", userId);
        return true;
    }

    @Override
    public boolean processPayment(Long payerId, Long payeeId, double amount) {
        log.info("[COTS Integration] Stripe transferred €{} from User {} to User {}",
                String.format("%.2f", amount), payerId, payeeId);
        return true;
    }
}

