package com.fptpost.modules.trip.dto.response;

import com.fptpost.modules.order.model.DeliveryUrgency;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin đơn hàng ghép vào chuyến đi")
public class TripAttachedOrderResponse {

    private Long id;
    private String orderCode;
    private Long senderId;
    private String pickupAddress;
    private BigDecimal pickupLatitude;
    private BigDecimal pickupLongitude;
    private String deliveryAddress;
    private BigDecimal deliveryLatitude;
    private BigDecimal deliveryLongitude;
    private String productName;
    private Integer quantity;
    private BigDecimal weightKg;
    private BigDecimal declaredValue;
    private BigDecimal priceSuggested;
    private BigDecimal priceAgreed;
    private BigDecimal distanceKm;
    private DeliveryUrgency urgency;
    private String deliveryNote;
    private OrderStatus status;
    private String receiverName;
    private String receiverPhone;
    private Instant createdAt;
    private Double detourKm;

    public static TripAttachedOrderResponse fromOrder(Order order) {
        return fromOrder(order, null);
    }

    public static TripAttachedOrderResponse fromOrder(Order order, Double detourKm) {
        if (order == null) return null;
        return TripAttachedOrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .senderId(order.getSenderId())
                .pickupAddress(order.getPickupAddress())
                .pickupLatitude(order.getPickupLatitude())
                .pickupLongitude(order.getPickupLongitude())
                .deliveryAddress(order.getDeliveryAddress())
                .deliveryLatitude(order.getDeliveryLatitude())
                .deliveryLongitude(order.getDeliveryLongitude())
                .productName(order.getProductName())
                .quantity(order.getQuantity())
                .weightKg(order.getWeightKg())
                .declaredValue(order.getDeclaredValue())
                .priceSuggested(order.getPriceSuggested())
                .priceAgreed(order.getPriceAgreed())
                .distanceKm(order.getDistanceKm())
                .urgency(order.getUrgency())
                .deliveryNote(order.getDeliveryNote())
                .status(order.getStatus())
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .createdAt(order.getCreatedAt())
                .detourKm(detourKm)
                .build();
    }
}
