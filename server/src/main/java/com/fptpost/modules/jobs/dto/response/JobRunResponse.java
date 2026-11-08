package com.fptpost.modules.jobs.dto.response;

import com.fptpost.modules.jobs.enums.JobRunStatus;
import com.fptpost.modules.jobs.enums.JobTrigger;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JobRunResponse {
    Long id;
    String jobName;
    JobTrigger trigger;
    JobRunStatus status;
    int attempts;
    int processed;
    String message;
    String instanceId;
    Instant startedAt;
    Instant finishedAt;
    Long durationMs;
}
