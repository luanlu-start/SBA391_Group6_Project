package com.fptpost.modules.order.model;

/**
 * Các trạng thái trong vòng đời đơn hàng chuyển phát của FPTPost.
 */
public enum OrderStatus {
    PENDING,            // Đơn đã gửi lên hệ thống, chờ xử lý
    PENDING_ESCROW,     // Đơn chờ thanh toán ký quỹ (UC-35)
    ESCROWED,           // Đã thanh toán ký quỹ an toàn (UC-35)
    FINDING_SHIPPER,    // Đang tìm người giao, chờ báo giá
    SHIPPER_ASSIGNED,   // Đã lựa chọn hoặc chỉ định người giao
    IN_DELIVERY,        // Đang thực hiện giao hàng
    COMPLETED,          // Giao hàng thành công
    CANCELLED           // Đơn đã bị hủy
}
