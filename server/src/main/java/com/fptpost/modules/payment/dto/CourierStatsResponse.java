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
    /** Null khi chưa có dữ liệu xác nhận giao hàng. */
    private Double onTimeRate;
    /** Null khi chưa có đánh giá. */
    private Double ratingAvg;
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
