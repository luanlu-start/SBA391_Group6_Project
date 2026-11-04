package com.fptpost.modules.pricing;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.dto.response.UserDirectoryEntry;
import com.fptpost.modules.identity.service.UserDirectoryLookup;
import com.fptpost.modules.pricing.dto.request.PromotionSearchRequest;
import com.fptpost.modules.pricing.dto.request.PromotionWriteRequest;
import com.fptpost.modules.pricing.enums.*;
import com.fptpost.modules.pricing.exception.PromotionErrorCode;
import com.fptpost.modules.pricing.model.Promotion;
import com.fptpost.modules.pricing.repository.*;
import com.fptpost.modules.pricing.service.PromotionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromotionServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");
    private final PromotionRepository promotions = mock(PromotionRepository.class);
    private final PromotionUsageRepository usages = mock(PromotionUsageRepository.class);
    private final PromotionSearchRepository search = mock(PromotionSearchRepository.class);
    private final UserDirectoryLookup users = mock(UserDirectoryLookup.class);
    private final AuditOutboxService audit = mock(AuditOutboxService.class);
    private final PromotionService service = new PromotionService(promotions, usages, search, users, audit,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test void createNormalizesCodePreservesLargeAmountAndEnqueuesTheFlushedVersion() {
        var request = write();
        request.setCode("  sale_26  ");
        request.setValue("9999999999999999");
        flushVersions();
        var result = service.create(request);
        assertThat(result.code()).isEqualTo("SALE_26");
        assertThat(result.value()).isEqualTo("9999999999999999");
        assertThat(result.usedCount()).isZero();
        assertThat(result.version()).isZero();
        assertThat(result.effectiveStatus()).isEqualTo(PromotionEffectiveStatus.ACTIVE);
        assertThat(result.targetUserIds()).isEmpty();
        verify(audit).enqueue(eq("PROMOTION_CREATED"), eq("PROMOTION"), eq(result.id().toString()), eq(Map.of()),
                argThat(after -> after.get("value").equals("9999999999999999") && after.get("version").equals(0L)));
        verifyNoInteractions(users, usages);
    }

    @Test void percentAllowsTwoDecimalPlacesAndHydratesSelectedUsers() {
        UUID user = UUID.randomUUID();
        when(users.lookup(any())).thenReturn(List.of(new UserDirectoryEntry(user, "Trần Công Thuận", "thuan@example.invalid")));
        var request = write(); request.setType(PromotionType.PERCENTAGE); request.setValue("12.50");
        request.setTarget(PromotionTarget.SELECTED_USERS); request.setTargetUserIds(List.of(user));
        flushVersions();
        var result = service.create(request);
        assertThat(result.value()).isEqualTo("12.5");
        assertThat(result.targetUserIds()).containsExactly(user);
        assertThat(result.targetUsers()).singleElement().satisfies(option -> {
            assertThat(option.value()).isEqualTo(user.toString());
            assertThat(option.label()).isEqualTo("Trần Công Thuận");
            assertThat(option.description()).isEqualTo("thuan@example.invalid");
        });
    }

    @Test void rejectsInvalidNumbersWithoutReadingOrWritingStores() {
        for (String invalid : List.of("0", "-1", "+1", "1e3", "1,000", "NaN", ".5", "1.001", "10000000000000000")) {
            var request = write(); request.setValue(invalid); failure(() -> service.create(request), PromotionErrorCode.INVALID_VALUE);
        }
        var fractionVnd = write(); fractionVnd.setValue("1.25");
        failure(() -> service.create(fractionVnd), PromotionErrorCode.INVALID_VALUE);
        var percent = write(); percent.setType(PromotionType.PERCENTAGE); percent.setValue("100.01");
        failure(() -> service.create(percent), PromotionErrorCode.INVALID_VALUE);
        verifyNoInteractions(promotions, usages, users, search, audit);
    }

    @Test void acceptsWholeDongExpressedWithTrailingZeroesAndHundredPercent() {
        flushVersions();
        var fixed = write(); fixed.setValue("2000.00");
        assertThat(service.create(fixed).value()).isEqualTo("2000");
        var percent = write(); percent.setType(PromotionType.PERCENTAGE); percent.setValue("100.00");
        assertThat(service.create(percent).value()).isEqualTo("100");
    }

    @Test void validatesTimeAndBoundsBeforeAnyPersistence() {
        List<Consumer<PromotionWriteRequest>> corruptions = List.of(
                r -> r.setCode("ab"), r -> r.setCode("SALE WITH SPACE"), r -> r.setCode("a".repeat(51)),
                r -> r.setType(null), r -> r.setStatus(null), r -> r.setTarget(null), r -> r.setUsageLimit(0),
                r -> r.setValidFrom(Instant.MIN), r -> r.setValidTo(Instant.MAX), r -> r.setValidFrom(null),
                r -> r.setValidTo(r.getValidFrom()), r -> r.setValidTo(r.getValidFrom().minusNanos(1)),
                r -> { r.setValidFrom(NOW.plusNanos(1)); r.setValidTo(NOW.plusNanos(999)); });
        corruptions.forEach(corruption -> {
            var request = write(); corruption.accept(request);
            failure(() -> service.create(request), PromotionErrorCode.INVALID_VALUE);
        });
        failure(() -> service.create(null), PromotionErrorCode.INVALID_VALUE);
        verifyNoInteractions(promotions, usages, users, search, audit);
    }

    @Test void rejectsNullDuplicateExcessAndInconsistentTargets() {
        UUID id = UUID.randomUUID();
        var nullEntry = write(); nullEntry.setTargetUserIds(Arrays.asList((UUID) null));
        var duplicate = write(); duplicate.setTarget(PromotionTarget.SELECTED_USERS); duplicate.setTargetUserIds(List.of(id, id));
        var tooMany = write(); tooMany.setTarget(PromotionTarget.SELECTED_USERS);
        tooMany.setTargetUserIds(java.util.stream.IntStream.range(0, 101).mapToObj(i -> UUID.randomUUID()).toList());
        var allWithUser = write(); allWithUser.setTargetUserIds(List.of(id));
        var selectedEmpty = write(); selectedEmpty.setTarget(PromotionTarget.SELECTED_USERS);
        var nullList = write(); nullList.setTargetUserIds(null);
        for (var request : List.of(nullEntry, duplicate, tooMany, allWithUser, selectedEmpty, nullList)) {
            failure(() -> service.create(request), PromotionErrorCode.TARGET_INVALID);
        }
        verifyNoInteractions(promotions, usages, users, search, audit);
    }

    @Test void nonexistentTargetIsRejectedWithoutSavingOrAuditing() {
        var request = write(); request.setTarget(PromotionTarget.SELECTED_USERS); request.setTargetUserIds(List.of(UUID.randomUUID()));
        when(users.lookup(any())).thenReturn(List.of());
        failure(() -> service.create(request), PromotionErrorCode.TARGET_INVALID);
        verify(promotions, never()).saveAndFlush(any()); verifyNoInteractions(audit, usages);
    }

    @Test void duplicateCanonicalCodeAndUniqueConstraintRaceBothReturnConflictWithoutAudit() {
        var request = write(); request.setCode(" sale_26 ");
        when(promotions.existsByCode("SALE_26")).thenReturn(true);
        failure(() -> service.create(request), PromotionErrorCode.CODE_ALREADY_EXISTS);
        verify(promotions, never()).saveAndFlush(any());
        when(promotions.existsByCode("SALE_26")).thenReturn(false);
        when(promotions.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_promotions_code"));
        failure(() -> service.create(request), PromotionErrorCode.CODE_ALREADY_EXISTS);
        verifyNoInteractions(audit);
    }

    @Test void updateRejectsStaleVersionImmutableCodeAndLimitBelowRealUsages() {
        Promotion promotion = stored(); UUID id = promotion.getId();
        when(promotions.findByIdForUpdate(id)).thenReturn(Optional.of(promotion));
        var request = write(); request.setExpectedVersion(2L);
        failure(() -> service.update(id, request), PromotionErrorCode.VERSION_CONFLICT);
        request.setExpectedVersion(3L); request.setCode("OTHER26");
        failure(() -> service.update(id, request), PromotionErrorCode.CODE_IMMUTABLE);
        request.setCode(promotion.getCode()); request.setUsageLimit(2); when(usages.countByPromotionId(id)).thenReturn(3L);
        failure(() -> service.update(id, request), PromotionErrorCode.USAGE_LIMIT_BELOW_USED);
        assertThat(promotion.getUsageLimit()).isEqualTo(10);
        verify(promotions, never()).saveAndFlush(any()); verifyNoInteractions(audit, users);
    }

    @Test void updatePausesAndEmitsBeforeAfterWithActualUsageAndNewVersion() {
        Promotion promotion = stored(); UUID id = promotion.getId();
        when(promotions.findByIdForUpdate(id)).thenReturn(Optional.of(promotion));
        when(usages.countByPromotionId(id)).thenReturn(4L);
        flushVersions();
        var request = write(); request.setExpectedVersion(3L); request.setStatus(PromotionStatus.PAUSED); request.setValue("3000");
        var result = service.update(id, request);
        assertThat(result.version()).isEqualTo(4); assertThat(result.usedCount()).isEqualTo(4);
        assertThat(result.effectiveStatus()).isEqualTo(PromotionEffectiveStatus.PAUSED);
        @SuppressWarnings("unchecked") var before = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked") var after = ArgumentCaptor.forClass(Map.class);
        verify(audit).enqueue(eq("PROMOTION_UPDATED"), eq("PROMOTION"), eq(id.toString()), before.capture(), after.capture());
        assertThat(before.getValue()).containsEntry("value", "2000").containsEntry("version", 3L).containsEntry("usedCount", 4L);
        assertThat(after.getValue()).containsEntry("value", "3000").containsEntry("version", 4L).containsEntry("status", "PAUSED");
    }

    @Test void unavailableHistoricalTargetCanBeKeptButCannotBeNewlyAdded() {
        UUID deleted = UUID.randomUUID(); Promotion promotion = stored();
        promotion.setTarget(PromotionTarget.SELECTED_USERS); promotion.getTargetUserIds().add(deleted);
        when(promotions.findByIdForUpdate(promotion.getId())).thenReturn(Optional.of(promotion));
        when(users.lookup(any())).thenReturn(List.of()); flushVersions();
        var request = write(); request.setExpectedVersion(3L); request.setTarget(PromotionTarget.SELECTED_USERS);
        request.setTargetUserIds(List.of(deleted));
        var result = service.update(promotion.getId(), request);
        assertThat(result.targetUsers()).singleElement().satisfies(user -> {
            assertThat(user.value()).isEqualTo(deleted.toString());
            assertThat(user.description()).contains("không còn");
        });
    }

    @Test void effectiveStatusUsesFixedClockAndActualUsageWithExclusiveEnd() {
        Promotion promotion = stored(); UUID id = promotion.getId();
        when(promotions.findById(id)).thenReturn(Optional.of(promotion));
        assertThat(service.detail(id).effectiveStatus()).isEqualTo(PromotionEffectiveStatus.ACTIVE);
        promotion.setValidFrom(NOW.plusSeconds(1));
        assertThat(service.detail(id).effectiveStatus()).isEqualTo(PromotionEffectiveStatus.SCHEDULED);
        promotion.setValidFrom(NOW); promotion.setValidTo(NOW);
        assertThat(service.detail(id).effectiveStatus()).isEqualTo(PromotionEffectiveStatus.EXPIRED);
        promotion.setValidTo(NOW.plusSeconds(1)); when(usages.countByPromotionId(id)).thenReturn(10L);
        assertThat(service.detail(id).effectiveStatus()).isEqualTo(PromotionEffectiveStatus.EXHAUSTED);
        promotion.setStatus(PromotionStatus.PAUSED);
        assertThat(service.detail(id).effectiveStatus()).isEqualTo(PromotionEffectiveStatus.PAUSED);
    }

    @Test void listUsesRepositoryCountsAndHydratesAllTargetUsersInPage() {
        Promotion promotion = stored(); UUID user = UUID.randomUUID(); promotion.setTarget(PromotionTarget.SELECTED_USERS);
        promotion.getTargetUserIds().add(user);
        when(search.find(any(), eq(NOW))).thenReturn(new PageImpl<>(List.of(new PromotionSearchRepository.Row(promotion, 7)),
                PageRequest.of(0, 20), 1));
        when(users.lookup(any())).thenReturn(List.of(new UserDirectoryEntry(user, null, "sender@example.invalid")));
        var result = service.list(new PromotionSearchRequest());
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().getFirst().usedCount()).isEqualTo(7);
        assertThat(result.content().getFirst().targetUsers().getFirst().label()).isEqualTo("sender@example.invalid");
        verifyNoInteractions(usages, promotions, audit);
    }

    @Test void listAndTargetSearchBoundsAreValidatedAndOptionsAreBounded() {
        var request = new PromotionSearchRequest(); request.setPage(-1);
        failure(() -> service.list(request), PromotionErrorCode.INVALID_VALUE);
        request.setPage(100001); failure(() -> service.list(request), PromotionErrorCode.INVALID_VALUE);
        request.setPage(0); request.setSize(101); failure(() -> service.list(request), PromotionErrorCode.INVALID_VALUE);
        request.setSize(20); request.setQ("x".repeat(51)); failure(() -> service.list(request), PromotionErrorCode.INVALID_VALUE);
        failure(() -> service.list(null), PromotionErrorCode.INVALID_VALUE);
        failure(() -> service.targetOptions("x".repeat(101), 30), PromotionErrorCode.INVALID_VALUE);
        failure(() -> service.targetOptions(null, 0), PromotionErrorCode.INVALID_VALUE);
        failure(() -> service.targetOptions(null, 51), PromotionErrorCode.INVALID_VALUE);
        verifyNoInteractions(search, users, audit);
        var user = new UserDirectoryEntry(UUID.randomUUID(), "Name", "name@example.invalid");
        when(users.search("Name", 2)).thenReturn(List.of(user, user));
        var result = service.targetOptions("Name", 1);
        assertThat(result.hasMore()).isTrue(); assertThat(result.options()).hasSize(1);
    }

    @Test void missingPromotionAndMissingUpdateVersionHaveStableErrors() {
        UUID id = UUID.randomUUID();
        failure(() -> service.detail(id), PromotionErrorCode.NOT_FOUND);
        failure(() -> service.detail(null), PromotionErrorCode.NOT_FOUND);
        failure(() -> service.update(id, write()), PromotionErrorCode.INVALID_VALUE);
        var request = write(); request.setExpectedVersion(0L);
        failure(() -> service.update(id, request), PromotionErrorCode.NOT_FOUND);
        verifyNoInteractions(audit, usages, users);
    }

    private void flushVersions() {
        when(promotions.saveAndFlush(any())).thenAnswer(invocation -> {
            Promotion value = invocation.getArgument(0);
            value.setVersion(value.getVersion() == null ? 0L : value.getVersion() + 1); return value;
        });
    }

    private static PromotionWriteRequest write() {
        var request = new PromotionWriteRequest(); request.setCode("SALE_26"); request.setType(PromotionType.FIXED_AMOUNT);
        request.setValue("2000"); request.setValidFrom(NOW.minusSeconds(3600)); request.setValidTo(NOW.plusSeconds(3600));
        request.setUsageLimit(10); request.setTarget(PromotionTarget.ALL); request.setTargetUserIds(List.of());
        request.setStatus(PromotionStatus.ACTIVE); return request;
    }

    private static Promotion stored() {
        return Promotion.builder().id(UUID.randomUUID()).code("SALE_26").type(PromotionType.FIXED_AMOUNT)
                .value(new BigDecimal("2000.00")).validFrom(NOW.minusSeconds(3600)).validTo(NOW.plusSeconds(3600))
                .usageLimit(10).target(PromotionTarget.ALL).status(PromotionStatus.ACTIVE).version(3L)
                .createdAt(NOW.minusSeconds(86400)).updatedAt(NOW.minusSeconds(86400)).build();
    }

    private static void failure(Runnable action, PromotionErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
