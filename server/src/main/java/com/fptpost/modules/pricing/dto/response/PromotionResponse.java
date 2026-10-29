package com.fptpost.modules.pricing.dto.response;

import com.fptpost.modules.pricing.enums.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PromotionResponse(UUID id, String code, PromotionType type, String value,
        Instant validFrom, Instant validTo, Integer usageLimit, long usedCount, PromotionTarget target,
        List<UUID> targetUserIds, List<TargetUser> targetUsers, PromotionStatus status,
        PromotionEffectiveStatus effectiveStatus, long version, Instant createdAt, Instant updatedAt) {
    public record TargetUser(String value, String label, String description) {}
}
