package com.fptpost.modules.jobs.service;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.jobs.dto.response.JobRunResponse;
import com.fptpost.modules.jobs.dto.response.JobStatusResponse;
import com.fptpost.modules.jobs.enums.JobRunStatus;
import com.fptpost.modules.jobs.enums.JobTrigger;
import com.fptpost.modules.jobs.model.ScheduledJobLock;
import com.fptpost.modules.jobs.model.ScheduledJobRun;
import com.fptpost.modules.jobs.repository.ScheduledJobLockRepository;
import com.fptpost.modules.jobs.repository.ScheduledJobRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class JobAdministrationService {

    private final JobRunner jobRunner;
    private final ScheduledJobLockRepository locks;
    private final ScheduledJobRunRepository runs;
    private final IdentityUserAccess userAccess;
    private final AuditOutboxService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<JobStatusResponse> list() {
        Instant now = clock.instant();
        return jobRunner.jobs().stream().map(job -> {
            ScheduledJobLock lock = locks.findById(job.name()).orElse(null);
            List<ScheduledJobRun> recent = runs.findTop10ByJobNameOrderByStartedAtDescIdDesc(job.name());
            int failures = 0;
            for (ScheduledJobRun run : recent) {
                if (run.getStatus() == JobRunStatus.SKIPPED) continue;
                if (run.getStatus() != JobRunStatus.FAILED) break;
                failures++;
            }
            Instant lastStarted = lock == null ? null : lock.getLastStartedAt();
            return JobStatusResponse.builder()
                    .name(job.name())
                    .title(job.title())
                    .description(job.description())
                    .intervalSeconds(job.interval().toSeconds())
                    .running(lock != null && lock.getLockedUntil() != null && lock.getLockedUntil().isAfter(now))
                    .nextRunAt(lastStarted == null ? now : lastStarted.plus(job.interval()))
                    .consecutiveFailures(failures)
                    .lastRun(recent.isEmpty() ? null : response(recent.get(0)))
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<JobRunResponse> history(String name, int page, int size) {
        jobRunner.job(name);
        return PageResponse.from(runs.findByJobNameOrderByStartedAtDescIdDesc(name,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)))).map(JobAdministrationService::response));
    }

    @Transactional
    public JobRunResponse runNow(UUID actorPublicId, String name) {
        User admin = userAccess.read(actorPublicId);
        BackgroundJob job = jobRunner.job(name);
        ScheduledJobRun run = jobRunner.run(job, JobTrigger.MANUAL, admin.getId());
        audit.enqueue("JOB_MANUAL_RUN", "SCHEDULED_JOB", name, Map.of(),
                Map.of("status", run.getStatus().name(), "processed", run.getProcessed()));
        return response(run);
    }

    static JobRunResponse response(ScheduledJobRun run) {
        return JobRunResponse.builder()
                .id(run.getId())
                .jobName(run.getJobName())
                .trigger(run.getTrigger())
                .status(run.getStatus())
                .attempts(run.getAttempts())
                .processed(run.getProcessed())
                .message(run.getMessage())
                .instanceId(run.getInstanceId())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .durationMs(run.getFinishedAt() == null ? null : Duration.between(run.getStartedAt(), run.getFinishedAt()).toMillis())
                .build();
    }
}
