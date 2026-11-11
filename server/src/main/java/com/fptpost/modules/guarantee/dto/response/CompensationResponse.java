package com.fptpost.modules.guarantee.dto.response;

import com.fptpost.modules.dispute.dto.response.ComplaintPerson;
import com.fptpost.modules.guarantee.enums.CompensationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CompensationResponse(
        UUID id,
        UUID complaintId,
        String complaintCode,
        String orderCode,
        BigDecimal declaredValue,
        ComplaintPerson beneficiary,
        long requestedAmount,
        Long approvedAmount,
        CompensationStatus status,
        Integer queuePosition,
        String note,
        Instant ruledAt,
        ComplaintPerson reviewedBy,
        Instant reviewedAt,
        Instant queuedAt,
        Instant paidAt) {
}
