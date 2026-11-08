package com.fptpost.modules.payment.repository;

import com.fptpost.modules.payment.model.WithdrawalRequest;
import com.fptpost.modules.payment.model.WithdrawalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long>,
        JpaSpecificationExecutor<WithdrawalRequest> {

    Optional<WithdrawalRequest> findByRequestCode(String requestCode);

    Optional<WithdrawalRequest> findByIdAndUserId(Long id, Long userId);

    Page<WithdrawalRequest> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<WithdrawalRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<WithdrawalRequest> findByStatus(WithdrawalStatus status);

    boolean existsByRequestCode(String requestCode);

    boolean existsByPayoutReference(String payoutReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WithdrawalRequest w where w.id = :id")
    Optional<WithdrawalRequest> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WithdrawalRequest w where w.requestCode = :requestCode")
    Optional<WithdrawalRequest> findByRequestCodeForUpdate(@Param("requestCode") String requestCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WithdrawalRequest w where w.status = :status and w.id in :ids order by w.createdAt asc, w.id asc")
    List<WithdrawalRequest> findByStatusAndIdInForUpdate(@Param("status") WithdrawalStatus status,
                                                         @Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WithdrawalRequest w where w.status = :status order by w.createdAt asc, w.id asc")
    List<WithdrawalRequest> findByStatusForUpdate(@Param("status") WithdrawalStatus status, Pageable pageable);

    long countByUserIdAndStatus(Long userId, WithdrawalStatus status);

    long countByUserIdAndStatusIn(Long userId, Collection<WithdrawalStatus> statuses);

    @Query("select coalesce(sum(w.amount), 0) from WithdrawalRequest w where w.userId = :userId and w.status = :status")
    long sumAmountByUserIdAndStatus(@Param("userId") Long userId, @Param("status") WithdrawalStatus status);

    @Query("select max(w.settledAt) from WithdrawalRequest w where w.userId = :userId and w.status = :status")
    Optional<Instant> findLastSettledAt(@Param("userId") Long userId, @Param("status") WithdrawalStatus status);
}
