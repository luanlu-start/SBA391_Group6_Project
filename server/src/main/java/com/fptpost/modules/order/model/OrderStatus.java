package com.fptpost.modules.order.model;

/**
 * Các trạng thái trong vòng đời đơn hàng chuyển phát của FPTPost.
 */
public enum OrderStatus {
    PENDING,            // Đơn đã gửi lên hệ thống, chờ xử lý
    FINDING_SHIPPER,    // Đang tìm người giao, chờ báo giá
    AWAITING_PAYMENT,   // Đã nhận/chọn người giao, chờ người gửi ký quỹ (UC-17)
    SHIPPER_ASSIGNED,   // Đã lựa chọn hoặc chỉ định người giao
    IN_DELIVERY,        // Đang thực hiện giao hàng
    COMPLETED,          // Giao hàng thành công
    CANCELLED           // Đơn đã bị hủy
}
