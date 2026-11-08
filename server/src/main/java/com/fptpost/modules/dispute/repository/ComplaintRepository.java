package com.fptpost.modules.dispute.repository;

import com.fptpost.modules.dispute.enums.ComplaintStatus;
import com.fptpost.modules.dispute.model.Complaint;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    Optional<Complaint> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Complaint c where c.publicId = :id")
    Optional<Complaint> findByPublicIdForUpdate(@Param("id") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Complaint c where c.id = :id")
    Optional<Complaint> findByIdForUpdate(@Param("id") Long id);

    boolean existsByOrderIdAndOpenedByAndStatusIn(Long orderId, Long openedBy, Collection<ComplaintStatus> statuses);

    @Query(value = """
            select c from Complaint c
            where c.status in :statuses and (:assignee is null or c.assignedTo = :assignee)
            order by c.resolveDueAt asc, c.id asc
            """, countQuery = """
            select count(c) from Complaint c
            where c.status in :statuses and (:assignee is null or c.assignedTo = :assignee)
            """)
    Page<Complaint> queue(@Param("statuses") Collection<ComplaintStatus> statuses, @Param("assignee") Long assignee, Pageable pageable);

    @Query(value = """
            select c from Complaint c, Order o
            where o.id = c.orderId and (o.senderId = :userId or o.courierId = :userId)
            order by c.createdAt desc, c.id desc
            """, countQuery = """
            select count(c) from Complaint c, Order o
            where o.id = c.orderId and (o.senderId = :userId or o.courierId = :userId)
            """)
    Page<Complaint> involving(@Param("userId") Long userId, Pageable pageable);

    @Query("select c.id from Complaint c where c.status = :status and c.appealDeadline <= :now order by c.ruledAt asc, c.id asc")
    List<Long> appealWindowEnded(@Param("status") ComplaintStatus status, @Param("now") Instant now);

    @Query("""
            select c.id from Complaint c
            where c.status in :statuses and c.firstResponseAt is null and c.responseAlertedAt is null
            and c.firstResponseDueAt <= :now order by c.firstResponseDueAt asc
            """)
    List<Long> responseOverdue(@Param("statuses") Collection<ComplaintStatus> statuses, @Param("now") Instant now);

    @Query("""
            select c.id from Complaint c
            where c.status in :statuses and c.resolveAlertedAt is null and c.resolveDueAt <= :now
            order by c.resolveDueAt asc
            """)
    List<Long> resolutionOverdue(@Param("statuses") Collection<ComplaintStatus> statuses, @Param("now") Instant now);
}
