package com.fptpost.modules.reputation.port;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * Điểm đánh giá trung bình người giao nhận được (UC-47). Phân hệ đánh giá cung cấp một bean triển khai interface này;
 * khi chưa có bean, thành phần đánh giá dùng giá trị trung tính TRUST_INIT.
 */
public interface CourierRatingSource {

    /** Chỉ trả người giao có ít nhất một đánh giá đã hiển thị (BR-R-071). */
    Map<Long, RatingSummary> summarize(Collection<Long> courierIds);

    /** @param average trung bình số sao từ 1 đến 5 */
    record RatingSummary(long count, BigDecimal average) {
    }
}
