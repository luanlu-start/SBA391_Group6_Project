package com.fptpost.modules.backoffice.config;

import com.fptpost.modules.backoffice.service.AccountLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.account-lock.release-enabled", havingValue = "true", matchIfMissing = true)
public class AccountLockReleaseJob {

    private final AccountLockService accountLockService;

    @Scheduled(fixedDelayString = "${app.account-lock.release-interval-ms:300000}", initialDelayString = "${app.account-lock.release-initial-delay-ms:60000}")
    public void releaseExpiredLocks() {
        int released = accountLockService.releaseExpired();
        if (released > 0) log.info("Released {} expired account locks", released);
    }
}
