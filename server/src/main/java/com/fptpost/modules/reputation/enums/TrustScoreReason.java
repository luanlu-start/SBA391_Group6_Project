package com.fptpost.modules.reputation.enums;

/** Nguyên nhân tính lại điểm uy tín, lưu cùng lịch sử để giải thích biến động (DB-038). */
public enum TrustScoreReason {
    NIGHTLY,
    COMPLAINT_CLOSED,
    RATING,
    ORDER_COMPLETED,
    CANCELLATION,
    ADMIN
}
