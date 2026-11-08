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
     * Đã duyệt cấp 1, chờ một quản trị viên khác duyệt cấp 2 (số tiền từ ngưỡng DUAL_APPROVE).
     */
    APPROVED_L1,

    /**
     * Đủ cấp duyệt, chờ chi trả.
     */
    APPROVED,

    /**
     * Đã gửi PayoutProvider hoặc đã xuất lệnh chi, chờ kết quả.
     */
    PROCESSING,

    /**
     * Đã chi thành công, có mã tham chiếu.
     */
    PAID,

    /**
     * Bị từ chối phê duyệt (kèm lý do), tiền đã hoàn về ví khả dụng.
     */
    REJECTED,

    /**
     * Chi trả thất bại, tiền đã hoàn về ví khả dụng.
     */
    FAILED,

    /**
     * Người dùng chủ động hủy yêu cầu khi còn ở trạng thái PENDING_REVIEW.
     */
    CANCELLED
}
