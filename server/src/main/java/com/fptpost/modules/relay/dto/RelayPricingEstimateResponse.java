package com.fptpost.modules.relay.dto;

import com.fptpost.modules.order.model.DeliveryUrgency;
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
public class RelayPricingEstimateResponse {
    private BigDecimal totalDistanceKm;
    private BigDecimal totalEstimatedPrice;
    private DeliveryUrgency urgency;
    private List<LegPricingBreakdownDto> legs;
}
