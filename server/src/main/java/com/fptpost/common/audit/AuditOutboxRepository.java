package com.fptpost.common.audit;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuditOutboxRepository extends JpaRepository<AuditOutboxEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from AuditOutboxEntity e where e.type = 'AUDIT' and ("
            + "(e.status = :pending and e.nextAttemptAt <= :now) "
            + "or (e.status = :processing and e.claimedAt <= :cutoff) "
            + ") order by e.id")
    List<AuditOutboxEntity> lockEligible(@Param("now") Instant now, @Param("cutoff") Instant cutoff,
                                       @Param("pending") AuditOutboxEntity.Status pending,
                                       @Param("processing") AuditOutboxEntity.Status processing,
                                       Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from AuditOutboxEntity e where e.id = :id")
    Optional<AuditOutboxEntity> lockById(@Param("id") Long id);
}
