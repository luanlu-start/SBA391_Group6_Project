package com.fptpost.modules.order.dto.response;

import com.fptpost.modules.order.model.OrderStatus;
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
public class OrderLegResponse {
    private Long id;
    private Integer legNo;
    private String pickupAddress;
    private BigDecimal pickupLatitude;
    private BigDecimal pickupLongitude;
    private String deliveryAddress;
    private BigDecimal deliveryLatitude;
    private BigDecimal deliveryLongitude;
    private BigDecimal distanceKm;
    private BigDecimal legFee;
    private Long courierId;
    private OrderStatus status;
    private Instant createdAt;
}
