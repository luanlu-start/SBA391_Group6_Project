package com.fptpost.modules.identity.config;

import com.fptpost.modules.identity.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "app.kyc.review-sla-enabled", havingValue = "true", matchIfMissing = true)
public class KycReviewSlaJob {
    private final KycReviewSlaPolicy sla;
    private final KycReviewSlaScanner scanner;
    private final KycReviewSlaAlertService alerts;
    private final Clock identityClock;

    @Scheduled(initialDelayString = "${app.kyc.review-sla-initial-delay-ms:60000}",
            fixedDelayString = "${app.kyc.review-sla-delay-ms:60000}")
    public void run() {
        try {
            long hours = sla.hours(); var now = identityClock.instant();
            for (var candidate : scanner.candidates(sla.cutoff(now, hours), hours)) {
                try { alerts.alert(candidate, hours, now); }
                catch (RuntimeException exception) { log.warn("KYC overdue alert failed; the next scan will retry"); }
            }
        } catch (RuntimeException exception) { log.warn("KYC overdue scan unavailable; the next scan will retry"); }
    }
}
