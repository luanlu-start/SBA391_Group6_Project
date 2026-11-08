package com.fptpost.modules.moderation.repository;

import com.fptpost.modules.moderation.enums.ViolationAction;
import com.fptpost.modules.moderation.enums.ViolationCaseStatus;
import com.fptpost.modules.moderation.enums.ViolationTargetType;
import com.fptpost.modules.moderation.model.ViolationCase;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ViolationCaseRepository extends JpaRepository<ViolationCase, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from ViolationCase c
            where c.targetType = :type and c.targetId = :targetId and c.status = com.fptpost.modules.moderation.enums.ViolationCaseStatus.OPEN
            order by c.createdAt asc, c.id asc
            """)
    List<ViolationCase> findOpenForUpdate(@Param("type") ViolationTargetType type, @Param("targetId") String targetId);

    Optional<ViolationCase> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ViolationCase c where c.publicId = :id")
    Optional<ViolationCase> findByPublicIdForUpdate(@Param("id") UUID publicId);

    @Query(value = """
            select c from ViolationCase c
            where c.status = :status and (:type is null or c.targetType = :type)
            order by c.reportCount desc, c.createdAt asc, c.id asc
            """, countQuery = """
            select count(c) from ViolationCase c where c.status = :status and (:type is null or c.targetType = :type)
            """)
    Page<ViolationCase> queue(@Param("status") ViolationCaseStatus status, @Param("type") ViolationTargetType type, Pageable pageable);

    long countByRespondentIdAndResolutionIn(Long respondentId, Collection<ViolationAction> resolutions);

    List<ViolationCase> findByIdIn(Collection<Long> ids);
}
