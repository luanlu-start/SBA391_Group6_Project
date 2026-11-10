package com.fptpost.modules.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierStatsResponse {
    private Long userId;
    private String currentTier;
    private String currentTierDisplayName;
    private BigDecimal commissionRate;
    private BigDecimal trustScore;
    private int totalAssignedOrders;
    private int completedOrders;
    private int cancelledOrders;
    private int inDeliveryOrders;
    private double completionRate;
    private double onTimeRate;
    private double ratingAvg;
    private double complaintRate;
    private String nextTier;
    private String nextTierDisplayName;
    private BigDecimal nextTierCommissionRate;
    private int nextTierMinOrders;
    private BigDecimal nextTierMinScore;
    private int ordersNeededForNextTier;
    private BigDecimal scoreNeededForNextTier;
    private int ordersProgressPercent;
    private int scoreProgressPercent;
}
