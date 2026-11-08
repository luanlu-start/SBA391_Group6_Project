package com.fptpost.modules.moderation.repository;

import com.fptpost.modules.moderation.model.ViolationReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ViolationReportRepository extends JpaRepository<ViolationReport, Long> {

    Optional<ViolationReport> findByCaseIdAndReporterId(Long caseId, Long reporterId);

    List<ViolationReport> findByCaseIdOrderByCreatedAtAscIdAsc(Long caseId);

    Page<ViolationReport> findByReporterIdOrderByCreatedAtDescIdDesc(Long reporterId, Pageable pageable);
}
