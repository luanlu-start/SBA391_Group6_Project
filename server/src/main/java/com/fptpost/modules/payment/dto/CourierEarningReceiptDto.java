package com.fptpost.modules.payment.dto;

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
public class CourierEarningReceiptDto {
    private Long orderId;
    private String orderCode;
    private String productName;
    private long grossAmount;
    private BigDecimal commissionRate;
    private long commissionAmount;
    private long netAmount;
    private String status;
    private Instant completedAt;
}
