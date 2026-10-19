package com.fptpost.modules.pricing.service;

import com.fptpost.modules.pricing.enums.*;
import com.fptpost.modules.pricing.model.Promotion;
import java.time.Instant;

final class PromotionState {
    private PromotionState() {}

    static PromotionEffectiveStatus effective(Promotion promotion, long used, Instant now) {
        if (promotion.getStatus() == PromotionStatus.PAUSED) return PromotionEffectiveStatus.PAUSED;
        if (!now.isBefore(promotion.getValidTo())) return PromotionEffectiveStatus.EXPIRED;
        if (now.isBefore(promotion.getValidFrom())) return PromotionEffectiveStatus.SCHEDULED;
        if (used >= promotion.getUsageLimit()) return PromotionEffectiveStatus.EXHAUSTED;
        return PromotionEffectiveStatus.ACTIVE;
    }
}
