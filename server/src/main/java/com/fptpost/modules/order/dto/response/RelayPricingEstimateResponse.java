package com.fptpost.modules.order.dto.response;

import com.fptpost.modules.order.model.DeliveryUrgency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelayPricingEstimateResponse {

    private BigDecimal totalDistanceKm;
    private BigDecimal totalEstimatedPrice;
    private DeliveryUrgency urgency;

    @Builder.Default
    private List<LegPricing> legs = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegPricing {
        private Integer legNo;
        private String fromAddress;
        private String toAddress;
        private BigDecimal distanceKm;
        private BigDecimal estimatedFee;
        private Double percentage;
    }
}
