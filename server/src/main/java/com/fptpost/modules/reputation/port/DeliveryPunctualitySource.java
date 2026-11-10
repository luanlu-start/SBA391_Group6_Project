package com.fptpost.modules.reputation.port;

import java.util.Collection;
import java.util.Map;

/**
 * Số đơn đã giao và số đơn giao đúng hạn của người giao (UC-28 xác nhận giao hàng). Khi chưa có bean triển khai,
 * thành phần đúng hạn dùng giá trị trung tính TRUST_INIT.
 */
public interface DeliveryPunctualitySource {

    Map<Long, Punctuality> summarize(Collection<Long> courierIds);

    record Punctuality(long delivered, long onTime) {
    }
}
