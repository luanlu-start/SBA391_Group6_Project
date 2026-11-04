package com.fptpost.common.audit;

import com.fptpost.modules.audit.retention.AuditRetentionProperties;
import com.fptpost.modules.audit.retention.AuditRetentionService;
import com.fptpost.modules.catalog.dto.internal.ParameterSnapshot;
import com.fptpost.modules.catalog.service.ParameterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class AuditRetentionServiceTest {
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ParameterService parameterService = mock(ParameterService.class);
    private final AuditRetentionProperties properties = new AuditRetentionProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2024-02-29T06:30:00Z"), ZoneId.of("America/Los_Angeles"));
    private final AuditRetentionService service = new AuditRetentionService(mongoTemplate, parameterService, properties, clock);

    @Test
    void cutoffUsesUtcCalendarYearsAcrossLeapDayRatherThanDaysOrServerTimezone() {
        when(parameterService.getSnapshot()).thenReturn(snapshot("1"));
        var result = service.purgeExpiredBatch();
        assertThat(result.cutoff()).isEqualTo(Instant.parse("2023-02-28T06:30:00Z"));
        assertThat(result.removed()).isZero();
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndRemove(query.capture(), eq(MongoAuditDocument.class));
        assertThat(query.getValue().getQueryObject()).containsOnlyKeys("at");
        assertThat(query.getValue().getQueryObject().get("at", org.bson.Document.class))
                .containsEntry("$lt", result.cutoff());
        assertThat(query.getValue().getSortObject()).containsEntry("at", 1).containsEntry("eventId", 1);
        assertThat(query.getValue().getLimit()).isEqualTo(1);
    }

    @Test
    void eachRunReadsCurrentAuditKeepWithoutCachingTheOldPolicy() {
        when(parameterService.getSnapshot()).thenReturn(snapshot("5"), snapshot("10"));
        assertThat(service.purgeExpiredBatch().cutoff()).isEqualTo(Instant.parse("2019-02-28T06:30:00Z"));
        assertThat(service.purgeExpiredBatch().cutoff()).isEqualTo(Instant.parse("2014-02-28T06:30:00Z"));
        verify(parameterService, times(2)).getSnapshot();
    }

    @Test
    void atomicBoundedBatchesPreserveTheBoundaryNewerAndFutureRecords() {
        when(parameterService.getSnapshot()).thenReturn(snapshot("5"));
        properties.setBatchMax(2);
        Instant cutoff = Instant.parse("2019-02-28T06:30:00Z");
        List<MongoAuditDocument> records = new ArrayList<>(List.of(
                document("old-1", cutoff.minusSeconds(3)), document("old-2", cutoff.minusSeconds(2)),
                document("old-3", cutoff.minusSeconds(1)), document("boundary", cutoff),
                document("newer", clock.instant()), document("future", clock.instant().plusSeconds(10))));
        when(mongoTemplate.findAndRemove(any(Query.class), eq(MongoAuditDocument.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            Instant before = query.getQueryObject().get("at", org.bson.Document.class).get("$lt", Instant.class);
            var expired = records.stream().filter(record -> record.at().isBefore(before))
                    .min(Comparator.comparing(MongoAuditDocument::at));
            expired.ifPresent(records::remove);
            return expired.orElse(null);
        });
        assertThat(service.purgeExpiredBatch().removed()).isEqualTo(2);
        assertThat(records).extracting(MongoAuditDocument::id).containsExactly("old-3", "boundary", "newer", "future");
        verify(mongoTemplate, times(2)).findAndRemove(any(Query.class), eq(MongoAuditDocument.class));
        assertThat(service.purgeExpiredBatch().removed()).isEqualTo(1);
        assertThat(records).extracting(MongoAuditDocument::id).containsExactly("boundary", "newer", "future");
        verify(mongoTemplate, times(4)).findAndRemove(any(Query.class), eq(MongoAuditDocument.class));
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void failedScheduledRunKeepsRemainingRecordsAndRetriesWithoutLoggingSecrets(CapturedOutput output) {
        when(parameterService.getSnapshot()).thenReturn(snapshot("5"));
        when(mongoTemplate.findAndRemove(any(Query.class), eq(MongoAuditDocument.class)))
                .thenThrow(new DataAccessResourceFailureException("mongodb://secret-user:secret-password@example"))
                .thenReturn(null);
        assertThatCode(service::scheduledPurge).doesNotThrowAnyException();
        assertThat(output).contains("Audit retention deferred (DataAccessResourceFailureException)")
                .doesNotContain("secret-user", "secret-password");
        assertThatCode(service::scheduledPurge).doesNotThrowAnyException();
        verify(mongoTemplate, times(2)).findAndRemove(any(Query.class), eq(MongoAuditDocument.class));
    }

    @Test
    void invalidOrUnrepresentablePolicyFailsClosedBeforeAnyMongoDeletion() {
        when(parameterService.getSnapshot()).thenReturn(snapshot("0"), snapshot("1000"), snapshot("1.5"));
        assertThatThrownBy(service::purgeExpiredBatch).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(service::purgeExpiredBatch).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(service::purgeExpiredBatch).isInstanceOf(ArithmeticException.class);
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void disabledSchedulingDoesNotReadParametersOrTouchMongo() {
        properties.setEnabled(false);
        service.scheduledPurge();
        verifyNoInteractions(parameterService, mongoTemplate);
    }

    private ParameterSnapshot snapshot(String years) {
        return new ParameterSnapshot(Map.of("AUDIT_KEEP", years), Map.of("AUDIT_KEEP", 0L), clock.instant());
    }

    private MongoAuditDocument document(String id, Instant at) {
        return new MongoAuditDocument(id, id, "USER", "actor", "UPDATED", "SYSTEM_PARAMETER", "PRM-107",
                Map.of(), Map.of(), null, null, at);
    }
}
