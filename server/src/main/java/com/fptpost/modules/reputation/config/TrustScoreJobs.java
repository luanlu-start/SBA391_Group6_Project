package com.fptpost.modules.reputation.config;

import com.fptpost.modules.reputation.enums.TrustScoreReason;
import com.fptpost.modules.reputation.service.TrustScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tính lại điểm uy tín và hạng của mọi người giao hằng đêm (BR-R-072). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.reputation.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class TrustScoreJobs {

    private final TrustScoreService trustScoreService;

    @Scheduled(cron = "${app.reputation.nightly-cron:0 30 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void nightly() {
        try {
            log.info("Nightly trust score run updated {} couriers", trustScoreService.recalculateAll(TrustScoreReason.NIGHTLY));
        } catch (RuntimeException exception) {
            log.warn("Nightly trust score run failed; the next run will retry", exception);
        }
    }
}
