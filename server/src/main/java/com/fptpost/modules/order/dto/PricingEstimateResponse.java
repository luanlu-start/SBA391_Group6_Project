package com.fptpost.modules.order.dto;

import com.fptpost.modules.order.model.DeliveryUrgency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingEstimateResponse {

    private BigDecimal distanceKm;
    private BigDecimal baseDistanceFee;
    private BigDecimal weightSurcharge;
    private BigDecimal insuranceFee;
    private DeliveryUrgency urgency;
    private BigDecimal urgencyMultiplier;
    private BigDecimal subtotalBeforeUrgency;
    private BigDecimal totalEstimatedPrice;
    private String calculationMethod;
}
