package com.fptpost.modules.pricing;

import com.fptpost.modules.pricing.enums.*;
import com.fptpost.modules.pricing.model.Promotion;
import com.fptpost.modules.pricing.repository.PromotionRepository;
import com.fptpost.modules.pricing.repository.PromotionUsageRepository;
import com.fptpost.modules.pricing.service.PromotionCatalog;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromotionCatalogTest {
    PromotionRepository promotions = mock(PromotionRepository.class);
    PromotionUsageRepository usages = mock(PromotionUsageRepository.class);
    Instant now = Instant.parse("2026-10-19T03:00:00Z");
    PromotionCatalog catalog = new PromotionCatalog(promotions, usages, Clock.fixed(now, ZoneOffset.UTC));

    @Test void canonicalLookupReturnsAnImmutableDetachedConfigurationWithActualUsageCount() {
        UUID userId = UUID.randomUUID();
        var promotion = Promotion.builder().id(UUID.randomUUID()).code("SALE26").type(PromotionType.PERCENTAGE)
                .value(new BigDecimal("10.50")).validFrom(now.minusSeconds(1)).validTo(now.plusSeconds(1))
                .usageLimit(10).target(PromotionTarget.SELECTED_USERS).status(PromotionStatus.ACTIVE).version(2L).build();
        promotion.getTargetUserIds().add(userId);
        when(promotions.findByCode("SALE26")).thenReturn(Optional.of(promotion));
        when(usages.countByPromotionId(promotion.getId())).thenReturn(4L);
        var snapshot = catalog.findByCode(" sale26 ").orElseThrow();
        assertThat(snapshot.capturedAt()).isEqualTo(now); assertThat(snapshot.usedCount()).isEqualTo(4);
        assertThat(snapshot.version()).isEqualTo(2);
        assertThat(snapshot.value()).isEqualByComparingTo("10.50");
        promotion.getTargetUserIds().clear(); promotion.setValue(BigDecimal.ONE);
        assertThat(snapshot.targetUserIds()).containsExactly(userId);
        assertThat(snapshot.value()).isEqualByComparingTo("10.50");
        assertThatThrownBy(() -> snapshot.targetUserIds().clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(promotions).findByCode("SALE26"); verify(usages).countByPromotionId(promotion.getId());
        verifyNoMoreInteractions(promotions, usages);
    }

    @Test void missingCodeIsEmptyAndMalformedCodeDoesNotQueryStorage() {
        when(promotions.findByCode("UNKNOWN")).thenReturn(Optional.empty());
        assertThat(catalog.findByCode("unknown")).isEmpty();
        assertThatThrownBy(() -> catalog.findByCode(null)).hasMessage("PROMOTION_INVALID_VALUE");
        assertThatThrownBy(() -> catalog.findByCode("BAD CODE")).hasMessage("PROMOTION_INVALID_VALUE");
        verify(promotions).findByCode("UNKNOWN"); verifyNoMoreInteractions(promotions); verifyNoInteractions(usages);
    }
}
