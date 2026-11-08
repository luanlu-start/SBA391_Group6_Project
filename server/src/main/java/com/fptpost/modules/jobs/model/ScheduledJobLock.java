package com.fptpost.modules.jobs.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Khóa phân tán theo tác vụ: chỉ instance cập nhật được dòng khóa mới chạy tác vụ trong chu kỳ đó (BR-N-015). */
@Entity
@Table(name = "scheduled_job_locks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledJobLock {

    @Id
    @Column(name = "job_name", length = 64)
    private String jobName;

    @Column(name = "locked_until", columnDefinition = "datetime2(6)")
    private Instant lockedUntil;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "last_started_at", columnDefinition = "datetime2(6)")
    private Instant lastStartedAt;

    @Column(name = "last_finished_at", columnDefinition = "datetime2(6)")
    private Instant lastFinishedAt;
}
