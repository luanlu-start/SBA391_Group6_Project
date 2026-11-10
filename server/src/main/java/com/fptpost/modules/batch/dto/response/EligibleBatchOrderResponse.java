package com.fptpost.modules.batch.dto.response;

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
public class EligibleBatchOrderResponse {

    private Long id;

    private String orderCode;

    private String productName;

    private String productDescription;

    private BigDecimal weightKg;

    private BigDecimal declaredValue;

    private BigDecimal priceAgreed;

    private String pickupAddress;

    private BigDecimal pickupLatitude;

    private BigDecimal pickupLongitude;

    private String deliveryAddress;

    private BigDecimal deliveryLatitude;

    private BigDecimal deliveryLongitude;

    private String receiverName;

    private String receiverPhone;

    private String status;

    private Long tripId;

    private Instant createdAt;
}
