package com.fptpost.modules.moderation.enums;

public enum OrderModerationStatus {
    /** Không khớp từ khóa hay danh mục cần kiểm tra, đơn lên sàn ngay. */
    AUTO_APPROVED,
    /** Đang chờ kiểm duyệt viên xem xét. */
    PENDING,
    APPROVED,
    REJECTED
}
