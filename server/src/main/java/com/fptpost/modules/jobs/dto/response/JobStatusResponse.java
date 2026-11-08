package com.fptpost.modules.jobs.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JobStatusResponse {
    String name;
    String title;
    String description;
    long intervalSeconds;
    boolean running;
    Instant nextRunAt;
    /** Số lần thất bại liên tiếp gần nhất. */
    int consecutiveFailures;
    JobRunResponse lastRun;
}
