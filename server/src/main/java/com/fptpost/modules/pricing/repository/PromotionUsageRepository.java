package com.fptpost.modules.pricing.repository;

import com.fptpost.modules.pricing.model.PromotionUsage;
import org.springframework.data.repository.Repository;
import java.util.UUID;

/** Read-only until the UC-44 applying owner supplies its atomic usage-writing workflow. */
public interface PromotionUsageRepository extends Repository<PromotionUsage, UUID> {
    long countByPromotionId(UUID promotionId);
}
