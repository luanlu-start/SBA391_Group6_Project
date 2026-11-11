package com.fptpost.modules.guarantee.repository;

import com.fptpost.modules.payment.model.LedgerTransaction;
import com.fptpost.modules.payment.model.LedgerTransactionType;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;

/** Biến động gần đây của Quỹ bảo đảm, chỉ đọc. */
public interface GuaranteeLedgerRepository extends Repository<LedgerTransaction, Long> {

    List<LedgerTransaction> findTop20ByTypeInOrderByCreatedAtDescIdDesc(Collection<LedgerTransactionType> types);
}
