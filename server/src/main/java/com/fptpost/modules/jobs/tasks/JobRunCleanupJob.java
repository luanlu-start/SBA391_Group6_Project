package com.fptpost.modules.jobs.tasks;

import com.fptpost.modules.jobs.repository.ScheduledJobRunRepository;
import com.fptpost.modules.jobs.service.BackgroundJob;
import com.fptpost.modules.jobs.service.JobResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class JobRunCleanupJob implements BackgroundJob {

    private final ScheduledJobRunRepository runs;

    @Value("${app.jobs.run-retention-days:90}")
    private int retentionDays;

    @Override
    public String name() {
        return "job-run-cleanup";
    }

    @Override
    public String title() {
        return "Dọn nhật ký tác vụ";
    }

    @Override
    public String description() {
        return "Xóa nhật ký chạy tác vụ cũ hơn thời hạn lưu.";
    }

    @Override
    public Duration interval() {
        return Duration.ofDays(1);
    }

    @Override
    public JobResult execute(Instant now) {
        int deleted = runs.deleteStartedBefore(now.minus(Duration.ofDays(retentionDays)));
        return new JobResult(deleted, "Đã xóa " + deleted + " nhật ký cũ hơn " + retentionDays + " ngày");
    }
}
