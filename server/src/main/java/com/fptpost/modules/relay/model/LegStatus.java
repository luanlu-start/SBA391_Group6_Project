package com.fptpost.modules.relay.model;

/**
 * Trạng thái của từng chặng trong đơn hàng giao tiếp sức.
 */
public enum LegStatus {
    PENDING,            // Chờ bắt đầu / chưa kích hoạt
    ASSIGNED,           // Đã phân công người giao cho chặng này
    IN_TRANSIT,         // Người giao đang vận chuyển hàng trên chặng này
    READY_FOR_HANDOVER, // Đã tới điểm hẹn, sẵn sàng bàn giao cho người giao tiếp theo
    HANDED_OVER,        // Đã bàn giao thành công cho người giao chặng kế tiếp
    COMPLETED,          // Hoàn tất (chặng cuối cùng đã giao cho người nhận)
    CANCELLED,          // Chặng bị hủy
    INCIDENT            // Phát sinh sự cố tại chặng hoặc điểm bàn giao
}
