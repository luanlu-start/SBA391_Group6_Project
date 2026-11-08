package com.fptpost.modules.dispute.config;

import com.fptpost.modules.dispute.service.ComplaintLifecycle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Đóng khiếu nại hết hạn kháng nghị và cảnh báo quá hạn SLA. Mỗi hồ sơ được khóa dòng trước khi xử lý
 * nên chạy đồng thời trên nhiều máy chủ không làm bút toán bị ghi hai lần.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.dispute.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class ComplaintJobs {

    private final ComplaintLifecycle lifecycle;

    @Scheduled(initialDelayString = "${app.dispute.jobs-initial-delay-ms:90000}",
            fixedDelayString = "${app.dispute.jobs-delay-ms:300000}")
    public void run() {
        try {
            int closed = lifecycle.closeExpiredAppealWindows();
            int alerts = lifecycle.alertOverdue();
            if (closed > 0 || alerts > 0) log.info("Complaint jobs closed {} complaints and sent {} overdue alerts", closed, alerts);
        } catch (RuntimeException exception) {
            log.warn("Complaint jobs unavailable; the next run will retry", exception);
        }
    }
}
