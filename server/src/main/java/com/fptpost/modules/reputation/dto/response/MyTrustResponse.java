package com.fptpost.modules.reputation.dto.response;

import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.reputation.enums.TrustScoreReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Điểm uy tín, hạng, quyền lợi và tiến độ lên hạng của người giao đang đăng nhập (API-121, BR-F-119). */
public record MyTrustResponse(
        BigDecimal score,
        CourierTier tier,
        String tierName,
        long completedOrders,
        List<Component> components,
        long recentCancellations,
        long cancelLimit,
        Instant restrictedUntil,
        boolean belowRestrictThreshold,
        NextTier nextTier,
        Benefits benefits,
        Instant calculatedAt,
        List<HistoryItem> history) {

    /** @param value tỷ lệ 0–1 sau làm mượt; {@code hasData = false} nghĩa là đang dùng giá trị khởi tạo */
    public record Component(String key, BigDecimal weight, BigDecimal value, boolean hasData) {
    }

    public record NextTier(CourierTier tier, String name, BigDecimal minScore, long minOrders) {
    }

    public record Benefits(int maxConcurrentOrders, BigDecimal maxTotalDeclaredValue, boolean canAcceptHighValue,
                           BigDecimal commissionRate) {
    }

    public record HistoryItem(BigDecimal score, BigDecimal previousScore, CourierTier tier, CourierTier previousTier,
                              TrustScoreReason reason, String note, Instant calculatedAt) {
    }
}
