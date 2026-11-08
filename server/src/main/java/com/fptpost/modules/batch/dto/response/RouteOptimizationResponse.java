package com.fptpost.modules.batch.dto.response;

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
public class RouteOptimizationResponse {

    private Integer totalOrders;

    private Integer totalStops;

    private BigDecimal totalWeightKg;

    private BigDecimal maxCapacityKg;

    private Boolean isCapacityExceeded;

    private BigDecimal totalDistanceKm;

    private Integer estimatedDurationMinutes;

    private List<BatchStopResponse> stops;

    private String optimizationMethod;

    private String message;
}
