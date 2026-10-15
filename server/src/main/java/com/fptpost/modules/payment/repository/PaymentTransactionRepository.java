package com.fptpost.modules.payment.repository;

import com.fptpost.modules.payment.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA Repository for PaymentTransaction.
 */
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByTxnRef(String txnRef);

    Optional<PaymentTransaction> findFirstByOrderCodeOrderByCreatedAtDesc(String orderCode);

    boolean existsByTxnRef(String txnRef);
}
