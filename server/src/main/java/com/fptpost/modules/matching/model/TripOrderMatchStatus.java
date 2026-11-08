package com.fptpost.modules.matching.model;

/**
 * Trạng thái kết quả ghép đơn - chuyến theo đặc tả DB-021 (trip_order_matches).
 */
public enum TripOrderMatchStatus {
    SUGGESTED,  // Đang gợi ý cho người giao hoặc người gửi
    ACCEPTED,   // Đã được nhận đơn hoặc chấp thuận ghép
    REJECTED,   // Bị từ chối hoặc bỏ qua
    EXPIRED     // Hết hạn hiệu lực ghép
}
