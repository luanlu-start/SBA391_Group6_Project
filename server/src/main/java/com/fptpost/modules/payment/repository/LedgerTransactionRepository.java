package com.fptpost.modules.payment.repository;

import com.fptpost.modules.payment.model.LedgerTransaction;
import com.fptpost.modules.payment.model.LedgerTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    Optional<LedgerTransaction> findByTransactionCode(String transactionCode);

    Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("""
        SELECT DISTINCT t FROM LedgerTransaction t
        JOIN t.entries e
        WHERE (e.accountCode = :accountCode OR t.createdBy = :userId)
          AND (:type IS NULL OR t.type = :type)
          AND (:fromDate IS NULL OR t.createdAt >= :fromDate)
          AND (:toDate IS NULL OR t.createdAt <= :toDate)
        ORDER BY t.createdAt DESC, t.id DESC
    """)
    Page<LedgerTransaction> findTransactions(
            @Param("userId") Long userId,
            @Param("accountCode") String accountCode,
            @Param("type") LedgerTransactionType type,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable
    );

    @Query("""
        SELECT DISTINCT t FROM LedgerTransaction t
        JOIN t.entries e
        WHERE (e.accountCode = :accountCode OR t.createdBy = :userId)
          AND (:fromDate IS NULL OR t.createdAt >= :fromDate)
          AND (:toDate IS NULL OR t.createdAt <= :toDate)
        ORDER BY t.createdAt ASC, t.id ASC
    """)
    List<LedgerTransaction> findTransactionsInRange(
            @Param("userId") Long userId,
            @Param("accountCode") String accountCode,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate
    );
}

