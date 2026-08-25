package com.routeshare.service.integration;


// what we need from a payment provider
public interface PaymentService {
    boolean verifyIdentity(Long userId);
    boolean processPayment(Long payerId, Long payeeId, double amount);
}
