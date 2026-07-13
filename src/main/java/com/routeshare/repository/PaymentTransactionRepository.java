package com.routeshare.repository;

import com.routeshare.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * PaymentTransactionRepository persists the payment ledger (FR-8 refinement).
 *
 * Demonstrates:
 * - Repository Pattern with derived queries: a user's statement (either side
 *   of the transfer) is answered by the database.
 */
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByPayerIdOrPayeeIdOrderByCreatedAtDesc(Long payerId, Long payeeId);

    List<PaymentTransaction> findByPayerIdOrPayeeId(Long payerId, Long payeeId);
}
