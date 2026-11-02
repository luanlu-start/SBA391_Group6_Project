package com.fptpost.modules.order.model;

/**
 * Trạng thái lời mời trực tiếp người giao hàng (UC-20 / DB-018)
 */
public enum InvitationStatus {
    PENDING,    // Đang chờ người giao phản hồi
    ACCEPTED,   // Người giao đã chấp nhận lời mời
    REJECTED,   // Người giao đã từ chối lời mời
    EXPIRED,    // Lời mời hết thời hạn hiệu lực
    CANCELLED   // Người gửi đã hủy lời mời trước khi người giao phản hồi
}
