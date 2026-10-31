package com.fptpost.modules.order.dto.response;

import com.fptpost.modules.order.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderDetailResponse (Long id,
                                   String orderCode,
                                   String pickupAddress,
                                   String deliveryAddress,
                                   String productName,
                                   Long categoryId,
                                   Integer quantity,
                                   BigDecimal weightKg,
                                   BigDecimal declaredValue,
                                   OrderStatus status,
                                   Instant createdAt) {
}
