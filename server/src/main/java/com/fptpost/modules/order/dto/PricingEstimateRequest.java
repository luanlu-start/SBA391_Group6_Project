package com.fptpost.modules.order.dto;

import com.fptpost.modules.order.model.DeliveryUrgency;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingEstimateRequest {

    private BigDecimal pickupLat;
    private BigDecimal pickupLng;
    private BigDecimal deliveryLat;
    private BigDecimal deliveryLng;

    private BigDecimal distanceKm;

    @DecimalMin(value = "0.001", message = "Weight must be greater than 0")
    private BigDecimal weightKg;

    private BigDecimal declaredValue;

    private DeliveryUrgency urgency;
}
