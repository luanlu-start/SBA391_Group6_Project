package com.fptpost.modules.relay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegPricingBreakdownDto {
    private Integer legNo;
    private String fromAddress;
    private String toAddress;
    private BigDecimal distanceKm;
    private BigDecimal estimatedFee;
    private Integer percentage;
}
