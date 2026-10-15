package com.fptpost.modules.payment.repository;

import com.fptpost.modules.payment.model.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    Optional<LedgerTransaction> findByTransactionCode(String transactionCode);

    Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
