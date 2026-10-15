package com.fptpost.modules.order.dto;

import com.fptpost.modules.order.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketplaceOrderResponse(
         Long id,
         String orderCode,
         String pickupAddress,
         String deliveryAddress,
         String productName,
         Integer quantity,
         BigDecimal weightKg,
         BigDecimal declaredValue,
         OrderStatus status,
         Instant createdAt
) {
}
