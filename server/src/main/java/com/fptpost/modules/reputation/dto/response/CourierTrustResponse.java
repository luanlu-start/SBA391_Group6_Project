package com.fptpost.modules.reputation.dto.response;

import com.fptpost.modules.dispute.dto.response.ComplaintPerson;
import com.fptpost.modules.identity.enums.CourierTier;

import java.math.BigDecimal;
import java.time.Instant;

public record CourierTrustResponse(
        ComplaintPerson courier,
        BigDecimal score,
        CourierTier tier,
        long completedOrders,
        long lostComplaints,
        long recentCancellations,
        boolean flaggedForReview,
        Instant restrictedUntil,
        Instant calculatedAt) {
}
