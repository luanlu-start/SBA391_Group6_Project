package com.fptpost.modules.payment.model;

/**
 * Trạng thái yêu cầu rút tiền (UC-40, UC-41).
 */
public enum WithdrawalStatus {
    /**
     * Yêu cầu mới tạo, đang chờ ban quản trị xét duyệt.
     */
    PENDING_REVIEW,

    /**
     * Đã được phê duyệt và giải ngân chi trả thành công về tài khoản ngân hàng.
     */
    APPROVED,

    /**
     * Bị từ chối phê duyệt (kèm lý do).
     */
    REJECTED,

    /**
     * Người dùng chủ động hủy yêu cầu khi còn ở trạng thái PENDING_REVIEW.
     */
    CANCELLED
}
