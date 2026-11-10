package com.fptpost.modules.batch.model;

/**
 * Trạng thái của một lượt giao gộp đơn hàng.
 */
public enum DeliveryBatchStatus {
    PLANNING,       // Đang lập lộ trình / kế hoạch
    IN_PROGRESS,    // Đang thực hiện giao hàng theo lộ trình
    COMPLETED,      // Đã hoàn tất toàn bộ các điểm dừng
    CANCELLED       // Đã hủy lượt giao gộp
}
