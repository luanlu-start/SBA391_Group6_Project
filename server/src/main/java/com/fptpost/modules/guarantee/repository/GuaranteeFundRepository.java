package com.fptpost.modules.guarantee.repository;

import com.fptpost.modules.guarantee.model.GuaranteeFundAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GuaranteeFundRepository extends JpaRepository<GuaranteeFundAccount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from GuaranteeFundAccount a where a.id = :id")
    Optional<GuaranteeFundAccount> findForUpdate(@Param("id") Long id);

    @Query("select coalesce(sum(e.credit), 0) - coalesce(sum(e.debit), 0) from LedgerEntry e where e.accountCode = :account")
    long ledgerBalance(@Param("account") String account);
}
