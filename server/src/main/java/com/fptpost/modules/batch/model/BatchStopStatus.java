package com.fptpost.modules.batch.model;

/**
 * Trạng thái thực hiện tại từng điểm dừng của đợt giao gộp.
 */
public enum BatchStopStatus {
    PENDING,    // Chưa đến
    ARRIVED,    // Đã có mặt tại điểm dừng
    COMPLETED,  // Đã hoàn thành (đã nhận hàng hoặc đã giao hàng)
    SKIPPED     // Bỏ qua do sự cố / không liên lạc được
}
