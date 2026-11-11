package com.fptpost.modules.guarantee.repository;

import com.fptpost.modules.dispute.enums.ComplaintStatus;
import com.fptpost.modules.dispute.model.Complaint;
import com.fptpost.modules.guarantee.enums.CompensationStatus;
import com.fptpost.modules.guarantee.model.Compensation;
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

public interface CompensationRepository extends JpaRepository<Compensation, Long> {

    boolean existsByComplaintId(Long complaintId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compensation c where c.complaintId = :complaintId")
    Optional<Compensation> findByComplaintIdForUpdate(@Param("complaintId") Long complaintId);

    @Query(value = "select c from Compensation c where c.status in :statuses order by c.ruledAt asc, c.id asc",
            countQuery = "select count(c) from Compensation c where c.status in :statuses")
    Page<Compensation> search(@Param("statuses") Collection<CompensationStatus> statuses, Pageable pageable);

    /** Hàng đợi chi theo thứ tự phán quyết. */
    @Query("select c from Compensation c where c.status = com.fptpost.modules.guarantee.enums.CompensationStatus.QUEUED "
            + "order by c.ruledAt asc, c.id asc")
    List<Compensation> queue();

    long countByStatus(CompensationStatus status);

    @Query("select c.status, count(c), coalesce(sum(coalesce(c.approvedAmount, c.requestedAmount)), 0) from Compensation c group by c.status")
    List<Object[]> totalsByStatus();

    /** Khiếu nại đã đóng có đề nghị bồi thường nhưng chưa có khoản bồi thường (đóng trước khi UC-52 chạy). */
    @Query("""
            select k from Complaint k where k.status = :closed and k.compensationAmount > 0
            and not exists (select c.id from Compensation c where c.complaintId = k.id)
            order by k.ruledAt asc, k.id asc
            """)
    List<Complaint> closedWithoutCompensation(@Param("closed") ComplaintStatus closed);
}
