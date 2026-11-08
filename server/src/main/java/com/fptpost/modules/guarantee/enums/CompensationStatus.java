package com.fptpost.modules.guarantee.enums;

/** Khoản bồi thường: chờ Admin duyệt, xếp hàng chờ quỹ (A1), đã chi hoặc bị từ chối. */
public enum CompensationStatus {
    PENDING_REVIEW,
    QUEUED,
    PAID,
    REJECTED
}
