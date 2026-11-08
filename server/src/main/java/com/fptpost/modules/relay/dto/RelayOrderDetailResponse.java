package com.fptpost.modules.relay.dto;

import com.fptpost.modules.order.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelayOrderDetailResponse {
    private Long orderId;
    private String orderCode;
    private String productName;
    private BigDecimal weightKg;
    private BigDecimal declaredValue;
    private BigDecimal totalPrice;
    private OrderStatus orderStatus;
    private Boolean isRelay;
    private Integer currentLegNo;
    private Integer totalLegs;
    private String pickupAddress;
    private String deliveryAddress;
    private String receiverName;
    private String receiverPhone;
    private List<OrderLegResponse> legs;
    private List<HandoverRecordDto> handoverHistory;
}
