package com.fptpost.modules.order.dto.response;

import com.fptpost.modules.order.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Chi tiết đơn hàng trên Sàn dành cho Bưu tá xem trước khi nhận (SCR-30, API-041).
 * Tuân thủ quy tắc BR-R-023 và BR-R-027 (ẩn dữ liệu nhạy cảm số nhà, SĐT người nhận).
 */
public record MarketplaceOrderDetailResponse(
        Long id,
        String orderCode,
        Long senderId,
        String pickupAddress,
        String deliveryAddress,
        String maskedReceiverName,
        String maskedReceiverPhone,
        String productName,
        String productDescription,
        Integer quantity,
        BigDecimal weightKg,
        BigDecimal declaredValue,
        BigDecimal priceSuggested,
        BigDecimal distanceKm,
        String deliveryNote,
        OrderStatus status,
        Instant createdAt,
        List<String> imageUrls,
        boolean requiresSilverTier,
        boolean isAvailable
) {
}
