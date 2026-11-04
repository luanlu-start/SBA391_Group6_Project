package com.fptpost.modules.order.model;

/**
 * Trạng thái của Chào giá (Offers) theo sheet 08_Trạng_thái.
 */
public enum OfferStatus {
    PENDING,    // Chờ phản hồi (chào giá hiệu lực, chờ người gửi)
    ACCEPTED,   // Đã chấp nhận (người gửi chấp nhận)
    REJECTED,   // Bị từ chối (người gửi từ chối hoặc hệ thống từ chối do chọn chào giá khác)
    COUNTERED,  // Đã phản giá (người gửi hoặc người giao đề xuất giá khác; sinh chào giá con)
    EXPIRED,    // Hết hạn (quá thời hạn hiệu lực 120 phút)
    WITHDRAWN   // Đã rút (người giao rút hoặc bị thay thế bởi chào giá mới)
}
