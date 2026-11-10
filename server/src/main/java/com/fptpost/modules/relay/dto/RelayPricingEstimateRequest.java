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
public class RelayPricingEstimateRequest {
    private BigDecimal pickupLat;
    private BigDecimal pickupLng;
    private BigDecimal deliveryLat;
    private BigDecimal deliveryLng;
    private BigDecimal weightKg;
    private BigDecimal declaredValue;
    private DeliveryUrgency urgency;
    private List<RelayPointDto> relayPoints;
}
