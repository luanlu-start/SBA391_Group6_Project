package com.fptpost.modules.catalog.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PricingSimulationResponse {
    private long suggestedFare;
    private long baseFare;
    private BigDecimal distanceFee;
    private long weightFee;
    private long sizeFee;
    private BigDecimal urgencyMultiplier;
    private long bidMinimum;
    private long bidMaximum;
    private long agreedFare;
    private BigDecimal commissionRate;
    private long commissionAmount;
    private long withholdingAmount;
    private long insuranceFee;
    private long promotionDiscount;
    private long guaranteeFundAmount;
    private long courierNet;
    private long platformNet;
    private long senderTotal;
    private long balanceDifference;
    private Map<String, Long> parameterVersions;
    private String mode;
}
