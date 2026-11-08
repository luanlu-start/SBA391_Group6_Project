package com.fptpost.modules.reporting.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Số liệu dashboard điều hành (UC-59, BR-F-114) trong khoảng ngày theo giờ Việt Nam, gồm cả hai đầu. */
public record DashboardResponse(
        LocalDate from,
        LocalDate to,
        long ordersCreated,
        Map<String, Long> ordersByStatus,
        double cancellationRate,
        long newUsers,
        long newCouriers,
        long activeCouriers,
        long escrowReleasedAmount,
        long escrowReleasedCount,
        long estimatedCommission,
        long topUpAmount,
        long withdrawalRequestedAmount,
        List<DailyPoint> daily) {

    public record DailyPoint(LocalDate date, long ordersCreated, long escrowReleasedAmount) {
    }
}
