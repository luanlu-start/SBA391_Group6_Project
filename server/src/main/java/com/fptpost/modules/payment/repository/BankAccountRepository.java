package com.fptpost.modules.payment.repository;

import com.fptpost.modules.payment.model.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    List<BankAccount> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<BankAccount> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndBankCodeIgnoreCaseAndAccountNoHash(Long userId, String bankCode, String accountNoHash);
}
