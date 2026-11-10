package com.fptpost.modules.jobs.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.jobs.enums.JobRunStatus;
import com.fptpost.modules.jobs.enums.JobTrigger;
import com.fptpost.modules.jobs.exception.JobErrorCode;
import com.fptpost.modules.jobs.model.ScheduledJobLock;
import com.fptpost.modules.jobs.model.ScheduledJobRun;
import com.fptpost.modules.jobs.repository.JobAlertRecipientRepository;
import com.fptpost.modules.jobs.repository.ScheduledJobLockRepository;
import com.fptpost.modules.jobs.repository.ScheduledJobRunRepository;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UC-63: chạy tác vụ nền với khóa phân tán, thử lại khi lỗi (A1), bỏ qua khi instance khác đang chạy (A2)
 * và ghi nhật ký từng lần chạy.
 */
@Slf4j
@Service
public class JobRunner {

    private final Map<String, BackgroundJob> jobs;
    private final ScheduledJobLockRepository locks;
    private final ScheduledJobRunRepository runs;
    private final JobAlertRecipientRepository alertRecipients;
    private final NotificationPublisher notifications;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String instanceId;
    private final int maxAttempts;
    private final Duration lease;

    public JobRunner(List<BackgroundJob> jobs, ScheduledJobLockRepository locks, ScheduledJobRunRepository runs,
                     JobAlertRecipientRepository alertRecipients, NotificationPublisher notifications,
                     PlatformTransactionManager transactionManager, Clock clock,
                     @Value("${app.jobs.max-attempts:2}") int maxAttempts,
                     @Value("${app.jobs.lock-lease-minutes:10}") long leaseMinutes) {
        this.jobs = jobs.stream().collect(Collectors.toMap(BackgroundJob::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        this.locks = locks;
        this.runs = runs;
        this.alertRecipients = alertRecipients;
        this.notifications = notifications;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.instanceId = hostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.lease = Duration.ofMinutes(Math.max(1, leaseMinutes));
    }

    public Collection<BackgroundJob> jobs() {
        return jobs.values();
    }

    public BackgroundJob job(String name) {
        BackgroundJob job = jobs.get(name);
        if (job == null) throw new AppException(JobErrorCode.JOB_NOT_FOUND);
        return job;
    }

    public void ensureLockRows() {
        for (String name : jobs.keySet()) {
            if (locks.existsById(name)) continue;
            try {
                transaction.executeWithoutResult(status -> locks.saveAndFlush(ScheduledJobLock.builder().jobName(name).build()));
            } catch (DataIntegrityViolationException exception) {
                log.debug("Lock row for job {} was created by another instance", name);
            }
        }
    }

    public boolean isDue(BackgroundJob job, Instant now) {
        Instant last = locks.findById(job.name()).map(ScheduledJobLock::getLastStartedAt).orElse(null);
        return last == null || !now.isBefore(last.plus(job.interval()));
    }

    public ScheduledJobRun run(BackgroundJob job, JobTrigger trigger, Long triggeredBy) {
        Instant started = clock.instant();
        Integer acquired = transaction.execute(status -> locks.acquire(job.name(), instanceId, started, started.plus(lease)));
        if (acquired == null || acquired == 0) {
            if (trigger == JobTrigger.MANUAL) throw new AppException(JobErrorCode.JOB_RUNNING);
            return record(job, trigger, triggeredBy, JobRunStatus.SKIPPED, 0, 0, "Instance khác đang chạy tác vụ này", started, clock.instant());
        }

        JobResult result = null;
        RuntimeException failure = null;
        int attempts = 0;
        try {
            while (attempts < maxAttempts && result == null) {
                attempts++;
                try {
                    result = transaction.execute(status -> job.execute(started));
                } catch (RuntimeException exception) {
                    failure = exception;
                    log.warn("Job {} attempt {} failed", job.name(), attempts, exception);
                }
            }
        } finally {
            transaction.executeWithoutResult(status -> locks.release(job.name(), instanceId, clock.instant()));
        }

        Instant finished = clock.instant();
        if (result != null) {
            return record(job, trigger, triggeredBy, JobRunStatus.SUCCESS, attempts, result.processed(), result.message(), started, finished);
        }
        String message = failure == null ? "Tác vụ không trả kết quả" : failure.getClass().getSimpleName()
                + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
        ScheduledJobRun run = record(job, trigger, triggeredBy, JobRunStatus.FAILED, attempts, 0, message, started, finished);
        alert(job, run);
        return run;
    }

    private ScheduledJobRun record(BackgroundJob job, JobTrigger trigger, Long triggeredBy, JobRunStatus status, int attempts,
                                   int processed, String message, Instant started, Instant finished) {
        String text = message == null ? null : message.length() > 1000 ? message.substring(0, 999) + "…" : message;
        return transaction.execute(tx -> runs.saveAndFlush(ScheduledJobRun.builder().jobName(job.name()).trigger(trigger)
                .triggeredBy(triggeredBy).instanceId(instanceId).status(status).attempts(attempts).processed(processed)
                .message(text).startedAt(started).finishedAt(finished).build()));
    }

    private void alert(BackgroundJob job, ScheduledJobRun run) {
        try {
            transaction.executeWithoutResult(status -> {
                for (UUID administrator : alertRecipients.findActiveAdministrators()) {
                    notifications.publish(administrator, NotificationCategory.SECURITY, "job-failed:" + job.name() + ":" + run.getId(),
                            "Tác vụ định kỳ thất bại", "Tác vụ \"" + job.title() + "\" lỗi sau " + run.getAttempts()
                                    + " lần thử. Xem nhật ký tại trang Tác vụ định kỳ.", "/admin/jobs");
                }
            });
        } catch (RuntimeException exception) {
            log.error("Could not alert administrators about failed job {}", job.name(), exception);
        }
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception exception) {
            return "instance";
        }
    }
}
