package com.fptpost.modules.guarantee.config;

import com.fptpost.modules.guarantee.service.GuaranteeFundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bổ sung khoản bồi thường còn thiếu và chi các khoản đang xếp hàng khi quỹ đủ. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.guarantee.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class GuaranteeJobs {

    private final GuaranteeFundService guaranteeFundService;

    @Scheduled(initialDelayString = "${app.guarantee.jobs-initial-delay-ms:120000}",
            fixedDelayString = "${app.guarantee.jobs-delay-ms:600000}")
    public void run() {
        try {
            int registered = guaranteeFundService.registerMissing();
            int paid = guaranteeFundService.processQueue();
            if (registered > 0 || paid > 0) log.info("Guarantee fund job registered {} and paid {} compensations", registered, paid);
        } catch (RuntimeException exception) {
            log.warn("Guarantee fund job unavailable; the next run will retry", exception);
        }
    }
}
