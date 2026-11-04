package com.fptpost.modules.pricing.dto.internal;

import com.fptpost.modules.pricing.enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Immutable UC-44 input; capture the applied discount and this configuration version on the order/ledger. */
public record PromotionSnapshot(UUID id, String code, PromotionType type, BigDecimal value, Instant validFrom,
        Instant validTo, int usageLimit, long usedCount, PromotionTarget target, Set<UUID> targetUserIds,
        PromotionStatus status, long version, Instant capturedAt) {
    public PromotionSnapshot { targetUserIds = Set.copyOf(targetUserIds); }
}
