package com.fptpost.modules.payment.repository;

import com.fptpost.modules.payment.model.ReconciliationReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface ReconciliationReportRepository extends JpaRepository<ReconciliationReport, Long> {

    Page<ReconciliationReport> findAllByOrderByReportDateDescCreatedAtDesc(Pageable pageable);

    Optional<ReconciliationReport> findFirstByReportDateOrderByCreatedAtDesc(LocalDate reportDate);
}
