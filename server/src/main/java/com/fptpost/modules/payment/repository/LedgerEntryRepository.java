package com.fptpost.modules.payment.repository;

import com.fptpost.modules.payment.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransactionId(Long transactionId);

    List<LedgerEntry> findByAccountCode(String accountCode);

    @Query("""
        SELECT COALESCE(SUM(e.credit), 0) - COALESCE(SUM(e.debit), 0)
        FROM LedgerEntry e
        JOIN e.transaction t
        WHERE e.accountCode = :accountCode
          AND t.status = com.fptpost.modules.payment.model.LedgerTransactionStatus.COMPLETED
          AND t.createdAt < :beforeInstant
    """)
    Long calculateBalanceBefore(
            @Param("accountCode") String accountCode,
            @Param("beforeInstant") Instant beforeInstant
    );
}

