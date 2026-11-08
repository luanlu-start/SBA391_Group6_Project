package com.fptpost.modules.batch.model;

/**
 * Loại điểm dừng trong lộ trình giao gộp: Lấy hàng hoặc Giao hàng.
 */
public enum BatchStopType {
    PICKUP,     // Điểm lấy hàng từ người gửi
    DELIVERY    // Điểm giao hàng cho người nhận
}
