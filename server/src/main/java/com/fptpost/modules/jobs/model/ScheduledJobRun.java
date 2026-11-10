package com.fptpost.modules.jobs.model;

import com.fptpost.modules.jobs.enums.JobRunStatus;
import com.fptpost.modules.jobs.enums.JobTrigger;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "scheduled_job_runs", indexes = @Index(name = "idx_job_runs_name_started", columnList = "job_name, started_at"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledJobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 64)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_trigger", nullable = false, length = 20)
    private JobTrigger trigger;

    @Column(name = "triggered_by")
    private Long triggeredBy;

    @Column(name = "instance_id", nullable = false, length = 100)
    private String instanceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobRunStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private int processed;

    @Column(length = 1000, columnDefinition = "nvarchar(1000)")
    private String message;

    @Column(name = "started_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant startedAt;

    @Column(name = "finished_at", columnDefinition = "datetime2(6)")
    private Instant finishedAt;
}
