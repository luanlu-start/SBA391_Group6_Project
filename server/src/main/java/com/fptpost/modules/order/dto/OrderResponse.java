package com.fptpost.modules.order.dto;

import com.fptpost.modules.order.model.DeliveryUrgency;
import com.fptpost.modules.order.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private String orderCode;
    private Long senderId;
    private Long courierId;
    private String pickupAddress;
    private BigDecimal pickupLatitude;
    private BigDecimal pickupLongitude;
    private String deliveryAddress;
    private BigDecimal deliveryLatitude;
    private BigDecimal deliveryLongitude;
    private String receiverName;
    private String receiverPhone;
    private String productName;
    private String productDescription;
    private Integer quantity;
    private BigDecimal weightKg;
    private BigDecimal declaredValue;
    private BigDecimal priceSuggested;
    private BigDecimal priceAgreed;
    private BigDecimal distanceKm;
    private DeliveryUrgency urgency;
    private String deliveryNote;
    private OrderStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant endedAt;
    private List<String> imageUrls;
}
