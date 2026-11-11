package com.fptpost.modules.backoffice.repository;

import com.fptpost.modules.backoffice.model.AccountLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountLockRepository extends JpaRepository<AccountLock, Long> {

    Optional<AccountLock> findFirstByUserIdAndUnlockedAtIsNullOrderByLockedAtDesc(Long userId);

    @Query("select l.id from AccountLock l where l.unlockedAt is null and l.lockedUntil is not null and l.lockedUntil <= :now order by l.lockedUntil asc")
    List<Long> findExpiredIds(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from AccountLock l where l.id = :id")
    Optional<AccountLock> findByIdForUpdate(@Param("id") Long id);
}
