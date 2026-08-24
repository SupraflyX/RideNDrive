package com.routeshare.service;

import com.routeshare.model.PaymentTransaction;
import com.routeshare.model.User;
import com.routeshare.repository.PaymentTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// our own record of every payment. PaymentService is the pretend gateway, this is
// what we store afterwards so receipts, statements and driver earnings have something
// permanent to read from
@Service
public class PaymentLedgerService {

    private final PaymentTransactionRepository repository;

    @Autowired
    public PaymentLedgerService(PaymentTransactionRepository repository) {
        this.repository = repository;
    }

    public PaymentTransaction record(User payer, User payee, double amount,
                                     PaymentTransaction.Status status, String memo, Long rideRequestId) {
        String reference = "PAY-" + Year.now().getValue() + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        return repository.save(new PaymentTransaction(reference, payer, payee,
                Math.round(amount * 100.0) / 100.0, status, memo, rideRequestId));
    }

    public List<PaymentTransaction> statementFor(Long userId) {
        return repository.findByPayerIdOrPayeeIdOrderByCreatedAtDesc(userId, userId);
    }
}
