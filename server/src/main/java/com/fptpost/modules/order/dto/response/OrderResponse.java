package com.fptpost.modules.order.dto.response;

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
    private String pickupAddress;
    private String deliveryAddress;
    private String receiverName;
    private String receiverPhone;
    private String productName;
    private String productDescription;
    private Integer quantity;
    private BigDecimal weightKg;
    private BigDecimal declaredValue;
    private String deliveryNote;
    private OrderStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<String> imageUrls;
}
