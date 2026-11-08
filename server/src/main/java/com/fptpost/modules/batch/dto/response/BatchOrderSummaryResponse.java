package com.fptpost.modules.batch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchOrderSummaryResponse {

    private Long orderId;

    private String orderCode;

    private String productName;

    private BigDecimal weightKg;

    private BigDecimal declaredValue;

    private BigDecimal priceAgreed;

    private String pickupAddress;

    private String deliveryAddress;

    private String receiverName;

    private String receiverPhone;

    private String status;
}
