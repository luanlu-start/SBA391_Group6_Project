package com.fptpost.modules.jobs.enums;

public enum JobRunStatus {
    SUCCESS,
    FAILED,
    /** Instance khác đang giữ khóa tác vụ nên lần chạy này bỏ qua (UC-63 A2). */
    SKIPPED
}
