package com.fptpost.modules.reporting.dto.response;

import java.util.UUID;

/**
 * Doanh thu một người giao trong tháng (BR-F-084, BR-C-010): tổng đã giải ngân, hoa hồng theo tỷ lệ phí khóa
 * trên đơn, khấu trừ thuế theo WITHHOLD và thu nhập ước tính sau khấu trừ.
 */
public record CourierRevenueResponse(
        UUID courierPublicId,
        String fullName,
        String email,
        long releasedOrders,
        long grossAmount,
        long commission,
        long withholding,
        long netIncome) {
}
