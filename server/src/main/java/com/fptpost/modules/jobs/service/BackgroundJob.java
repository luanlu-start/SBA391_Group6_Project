package com.fptpost.modules.jobs.service;

import java.time.Duration;
import java.time.Instant;

/**
 * Tác vụ nền do {@link JobRunner} điều phối. Mỗi lần chạy nằm trong một giao dịch và phải idempotent:
 * chạy lại cùng thời điểm không được tạo dữ liệu trùng (BR-N-015).
 */
public interface BackgroundJob {

    /** Mã ổn định, dùng trong URL và khóa phân tán. */
    String name();

    String title();

    String description();

    Duration interval();

    JobResult execute(Instant now);
}
