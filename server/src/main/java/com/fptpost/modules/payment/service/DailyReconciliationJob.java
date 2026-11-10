package com.fptpost.modules.payment.service;

import com.fptpost.modules.jobs.service.BackgroundJob;
import com.fptpost.modules.jobs.service.JobResult;
import com.fptpost.modules.payment.dto.ReconciliationReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Tác vụ nền đối soát giao dịch hằng ngày tự động (UC-43, PRM-072 RECONCILE_TIME).
 */
@Component
@RequiredArgsConstructor
public class DailyReconciliationJob implements BackgroundJob {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ReconciliationService reconciliationService;

    @Override
    public String name() {
        return "daily-reconciliation";
    }

    @Override
    public String title() {
        return "Đối soát giao dịch hằng ngày (UC-43)";
    }

    @Override
    public String description() {
        return "Đối chiếu giao dịch thanh toán VNPay và sổ cái kép nội bộ; cảnh báo Admin nếu phát hiện dòng lệch.";
    }

    @Override
    public Duration interval() {
        return Duration.ofHours(24);
    }

    @Override
    public JobResult execute(Instant now) {
        LocalDate targetDate = now.atZone(VIETNAM_ZONE).toLocalDate();
        ReconciliationReportResponse report = reconciliationService.runReconciliation(targetDate, null);
        return new JobResult(
                report.getTotalCount(),
                String.format("Đối soát ngày %s: tổng %d giao dịch (%d khớp, %d lệch)",
                        report.getReportDate(),
                        report.getTotalCount(),
                        report.getMatchedCount(),
                        report.getMismatchCount())
        );
    }
}
