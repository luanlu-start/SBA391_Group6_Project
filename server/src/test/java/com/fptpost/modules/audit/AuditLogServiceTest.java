package com.fptpost.modules.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.audit.AuditPayloadMasker;
import com.fptpost.common.audit.MongoAuditDocument;
import com.fptpost.common.exception.AppException;
import com.fptpost.modules.audit.dto.request.AuditLogSearchRequest;
import com.fptpost.modules.audit.exception.AuditErrorCode;
import com.fptpost.modules.audit.mapper.AuditLogMapper;
import com.fptpost.modules.audit.repository.AuditLogQueryRepository;
import com.fptpost.modules.audit.service.AuditLogService;
import com.mongodb.MongoException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditLogServiceTest {
    AuditLogQueryRepository repository = mock(AuditLogQueryRepository.class);
    AuditLogService service = new AuditLogService(repository, new AuditLogMapper(new AuditPayloadMasker(new ObjectMapper())));

    @Test void mapsPublicPageAndReappliesMaskingWithoutChangingStoredDocuments() {
        Map<String, Object> before = Map.of("value", "10000", "passwordHash", "old-secret",
                "nested", List.of(Map.of("email", "customer@example.invalid", "publicId", "public-user")));
        Map<String, Object> after = Map.of("value", "12000", "access_token", "token-secret");
        var doc = new MongoAuditDocument("internal", "event-id", "USER", "public-user", "PARAMETER_UPDATED",
                "system_parameters", "BASE_FARE", before, after, "127.0.0.1", "corr-id", Instant.EPOCH);
        when(repository.find(any())).thenReturn(new PageImpl<>(List.of(doc), PageRequest.of(0, 20), 1));
        var result = service.search(new AuditLogSearchRequest());
        assertThat(result.totalElements()).isEqualTo(1); assertThat(result.page()).isZero();
        var response = result.content().getFirst();
        assertThat(response.getEventId()).isEqualTo("event-id"); assertThat(response.getIp()).isEqualTo("127.0.0.1");
        assertThat(response.getCorrelationId()).isEqualTo("corr-id");
        assertThat(response.getBefore()).containsEntry("value", "10000").containsEntry("passwordHash", "[REDACTED]");
        assertThat(response.getAfter()).containsEntry("value", "12000").containsEntry("access_token", "[REDACTED]");
        assertThat(response.getBefore().get("nested")).isEqualTo(List.of(Map.of("email", "[REDACTED]", "publicId", "public-user")));
        assertThat(doc.before().get("passwordHash")).isEqualTo("old-secret");
        verify(repository).find(any()); verifyNoMoreInteractions(repository);
    }

    @Test void emptyStoreReturnsEmptyPageAndNullSnapshotsAreSafeObjects() {
        var doc = new MongoAuditDocument("internal", "event-id", "SYSTEM", null, "IMPORT", "orders", "order-id",
                null, null, null, null, Instant.EPOCH);
        when(repository.find(any())).thenReturn(new PageImpl<>(List.of(doc), PageRequest.of(0, 20), 1));
        var result = service.search(new AuditLogSearchRequest());
        assertThat(result.content().getFirst().getBefore()).isEmpty(); assertThat(result.content().getFirst().getAfter()).isEmpty();
        when(repository.find(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        result = service.search(new AuditLogSearchRequest());
        assertThat(result.content()).isEmpty(); assertThat(result.totalElements()).isZero();
        assertThat(result.first()).isTrue(); assertThat(result.last()).isTrue();
    }

    @Test void rejectsReversedTimeWithoutReadingStore() {
        var request = new AuditLogSearchRequest(); request.setFrom(Instant.parse("2026-10-15T00:00:00Z"));
        request.setTo(Instant.parse("2026-10-14T00:00:00Z"));
        invalid(request); verifyNoInteractions(repository);
    }

    @Test void rejectsUnsafeInstantAndPagingEvenForInternalCallers() {
        var request = new AuditLogSearchRequest(); request.setFrom(Instant.MIN); invalid(request);
        request.setFrom(null); request.setTo(Instant.MAX); invalid(request);
        request.setTo(null); request.setPage(AuditLogSearchRequest.MAX_PAGE + 1); invalid(request);
        request.setPage(0); request.setSize(0); invalid(request);
        request.setSize(101); invalid(request);
        request.setSize(20); request.setAction("A".repeat(101)); invalid(request);
        invalid(null); verifyNoInteractions(repository);
    }

    @Test void mongoUnavailableReturnsModule503WithoutDriverDetailsOrMemoryFallback() {
        when(repository.find(any())).thenThrow(new DataAccessResourceFailureException("credential=should-not-leak"));
        assertThatThrownBy(() -> service.search(new AuditLogSearchRequest())).isInstanceOfSatisfying(AppException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuditErrorCode.AUDIT_STORE_UNAVAILABLE))
                .hasMessage("AUDIT_STORE_UNAVAILABLE");
        doThrow(new MongoException("mongodb://private-host/should-not-leak")).when(repository).find(any());
        assertThatThrownBy(() -> service.search(new AuditLogSearchRequest())).hasMessage("AUDIT_STORE_UNAVAILABLE");
        verify(repository, times(2)).find(any()); verifyNoMoreInteractions(repository);
    }

    private void invalid(AuditLogSearchRequest request) {
        assertThatThrownBy(() -> service.search(request)).isInstanceOfSatisfying(AppException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuditErrorCode.AUDIT_INVALID_FILTER));
    }
}
