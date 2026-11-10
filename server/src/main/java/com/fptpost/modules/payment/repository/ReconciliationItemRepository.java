package com.fptpost.modules.payment.repository;

import com.fptpost.modules.payment.model.ReconciliationItem;
import com.fptpost.modules.payment.model.ReconciliationItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReconciliationItemRepository extends JpaRepository<ReconciliationItem, Long> {

    List<ReconciliationItem> findByReportIdOrderByIdAsc(Long reportId);

    long countByReportIdAndStatus(Long reportId, ReconciliationItemStatus status);
}
