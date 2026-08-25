package com.routeshare.repository;

import com.routeshare.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByPayerIdOrPayeeIdOrderByCreatedAtDesc(Long payerId, Long payeeId);

    List<PaymentTransaction> findByPayerIdOrPayeeId(Long payerId, Long payeeId);
}
