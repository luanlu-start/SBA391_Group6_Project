package com.fptpost.modules.payment.repository;

import com.fptpost.modules.payment.model.WithdrawalRequest;
import com.fptpost.modules.payment.model.WithdrawalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {

    Optional<WithdrawalRequest> findByRequestCode(String requestCode);

    Optional<WithdrawalRequest> findByIdAndUserId(Long id, Long userId);

    Page<WithdrawalRequest> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<WithdrawalRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<WithdrawalRequest> findByStatus(WithdrawalStatus status);

    boolean existsByRequestCode(String requestCode);
}
