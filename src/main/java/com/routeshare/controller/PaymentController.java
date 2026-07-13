package com.routeshare.controller;

import com.routeshare.model.PaymentTransaction;
import com.routeshare.service.PaymentLedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * PaymentController exposes the payment ledger (FR-8 refinement):
 *
 *   GET /api/payments/user/{userId}   the user's statement — paid and received,
 *                                     newest first, each with its PAY-… reference
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentLedgerService ledger;

    @Autowired
    public PaymentController(PaymentLedgerService ledger) {
        this.ledger = ledger;
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PaymentTransaction>> statement(@PathVariable Long userId) {
        return ResponseEntity.ok(ledger.statementFor(userId));
    }
}
