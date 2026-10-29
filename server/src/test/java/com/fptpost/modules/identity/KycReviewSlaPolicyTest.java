package com.fptpost.modules.identity;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.catalog.dto.internal.ParameterSnapshot;
import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.identity.service.KycReviewSlaPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KycReviewSlaPolicyTest {
    final ParameterService parameters = mock(ParameterService.class);
    final KycReviewSlaPolicy sla = new KycReviewSlaPolicy(parameters);

    @Test void usesCurrentCatalogValueAndContinuousHoursAcrossWeekends() {
        Instant friday = Instant.parse("2026-10-30T12:00:00Z");
        when(parameters.getSnapshot()).thenReturn(snapshot("24"));
        assertThat(sla.dueAt(friday, sla.hours())).isEqualTo(Instant.parse("2026-10-31T12:00:00Z"));
        when(parameters.getSnapshot()).thenReturn(snapshot("48"));
        assertThat(sla.dueAt(friday, sla.hours())).isEqualTo(Instant.parse("2026-11-01T12:00:00Z"));
    }

    @Test void invalidValuesAndOverflowFailWithoutInventingAFallbackDeadline() {
        for (String invalid : new String[]{"0", "-1", "1.5", "not-a-number"}) {
            when(parameters.getSnapshot()).thenReturn(snapshot(invalid));
            assertThatThrownBy(sla::hours).isInstanceOf(AppException.class);
        }
        assertThatThrownBy(() -> sla.dueAt(Instant.now(), Long.MAX_VALUE)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> sla.cutoff(Instant.now(), Long.MAX_VALUE)).isInstanceOf(AppException.class);
    }

    private ParameterSnapshot snapshot(String value) { return new ParameterSnapshot(Map.of("KYC_SLA", value), Map.of("KYC_SLA", 1L), Instant.EPOCH); }
}
