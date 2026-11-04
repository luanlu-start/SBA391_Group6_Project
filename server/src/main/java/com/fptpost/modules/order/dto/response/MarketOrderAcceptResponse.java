package com.fptpost.modules.order.dto.response;

import com.fptpost.modules.order.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Phản hồi khi Người giao nhận đơn thành công (UC-17, API-043).
 */
public record MarketOrderAcceptResponse(
        Long id,
        String orderCode,
        Long senderId,
        Long courierId,
        String pickupAddress,
        String deliveryAddress,
        String productName,
        Integer quantity,
        BigDecimal weightKg,
        BigDecimal declaredValue,
        BigDecimal priceAgreed,
        OrderStatus status,
        Instant acceptedAt,
        String message
) {
}
