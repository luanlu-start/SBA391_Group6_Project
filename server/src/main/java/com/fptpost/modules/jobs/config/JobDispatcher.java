package com.fptpost.modules.jobs.config;

import com.fptpost.modules.jobs.enums.JobTrigger;
import com.fptpost.modules.jobs.service.BackgroundJob;
import com.fptpost.modules.jobs.service.JobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class JobDispatcher {

    private final JobRunner jobRunner;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.jobs.tick-ms:30000}", initialDelayString = "${app.jobs.initial-delay-ms:60000}")
    public void tick() {
        Instant now = clock.instant();
        for (BackgroundJob job : jobRunner.jobs()) {
            try {
                if (jobRunner.isDue(job, now)) jobRunner.run(job, JobTrigger.SCHEDULED, null);
            } catch (RuntimeException exception) {
                log.error("Could not dispatch job {}", job.name(), exception);
            }
        }
    }
}
