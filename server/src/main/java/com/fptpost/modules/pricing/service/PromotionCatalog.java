package com.fptpost.modules.pricing.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.pricing.dto.internal.PromotionSnapshot;
import com.fptpost.modules.pricing.exception.PromotionErrorCode;
import com.fptpost.modules.pricing.repository.PromotionRepository;
import com.fptpost.modules.pricing.repository.PromotionUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

/**
 * Shared UC-44 read seam, not an apply/reserve endpoint. A snapshot alone does not reserve a usage.
 * The applying owner must enforce at most one code per order, cap the discount at the delivery fee,
 * charge the platform without reducing courier income, and record the accepted price/configuration
 * on the order/ledger. That owner must acquire PromotionRepository.findByIdForUpdate in its SQL
 * transaction BEFORE rechecking conditions and inserting usage/budget changes; this is the same
 * promotion lock taken by administrative edits. Never overwrite or delete previous usages.
 */
@Service @RequiredArgsConstructor
public class PromotionCatalog {
    private final PromotionRepository promotions;
    private final PromotionUsageRepository usages;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<PromotionSnapshot> findByCode(String suppliedCode) {
        String code = suppliedCode == null ? "" : suppliedCode.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9_-]{3,50}")) throw new AppException(PromotionErrorCode.INVALID_VALUE);
        return promotions.findByCode(code).map(promotion -> new PromotionSnapshot(promotion.getId(), promotion.getCode(),
                promotion.getType(), promotion.getValue(), promotion.getValidFrom(), promotion.getValidTo(),
                promotion.getUsageLimit(), usages.countByPromotionId(promotion.getId()), promotion.getTarget(),
                promotion.getTargetUserIds(), promotion.getStatus(), promotion.getVersion(), clock.instant()));
    }
}
