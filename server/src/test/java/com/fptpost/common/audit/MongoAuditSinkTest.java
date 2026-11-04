package com.fptpost.common.audit;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MongoAuditSinkTest {
    MongoTemplate mongo = mock(MongoTemplate.class);
    MongoAuditSink sink = new MongoAuditSink(mongo);

    @Test
    void appendUsesInsertAndUniqueIdIsTheEventUuid() {
        AuditEvent event = event();
        sink.append(event);
        verify(mongo).insert(argThat((MongoAuditDocument document) -> document.id().equals(event.eventId().toString())
                && document.eventId().equals(document.id()) && document.actorType().equals("USER")
                && document.actorPublicId().equals(event.actorPublicId().toString())));
        verifyNoMoreInteractions(mongo);
    }

    @Test
    void retryAfterCrashTreatsIdenticalStoredEventAsSuccess() {
        AuditEvent event = event();
        when(mongo.insert(any(MongoAuditDocument.class))).thenThrow(new DuplicateKeyException("duplicate _id"));
        when(mongo.findById(event.eventId().toString(), MongoAuditDocument.class)).thenReturn(MongoAuditDocument.from(event));
        assertThatCode(() -> sink.append(event)).doesNotThrowAnyException();
        verify(mongo).findById(event.eventId().toString(), MongoAuditDocument.class);
        verify(mongo, never()).save(any(MongoAuditDocument.class));
    }

    @Test
    void duplicateFromAnotherConstraintOrConflictingPayloadIsNotAcknowledged() {
        AuditEvent event = event();
        var duplicate = new DuplicateKeyException("another unique constraint");
        when(mongo.insert(any(MongoAuditDocument.class))).thenThrow(duplicate);
        assertThatThrownBy(() -> sink.append(event)).isSameAs(duplicate);
        var conflict = new AuditEvent(event.eventId(), event.actorPublicId(), "CONFLICTING_ACTION", event.entityType(),
                event.entityId(), event.before(), event.after(), event.ip(), event.correlationId(), event.at());
        when(mongo.findById(event.eventId().toString(), MongoAuditDocument.class)).thenReturn(MongoAuditDocument.from(conflict));
        assertThatThrownBy(() -> sink.append(event)).isSameAs(duplicate);
    }

    @Test
    void outageDuringDuplicateLookupRemainsRetryable() {
        AuditEvent event = event();
        when(mongo.insert(any(MongoAuditDocument.class))).thenThrow(new DuplicateKeyException("duplicate"));
        var outage = new DataAccessResourceFailureException("Mongo unavailable");
        when(mongo.findById(event.eventId().toString(), MongoAuditDocument.class)).thenThrow(outage);
        assertThatThrownBy(() -> sink.append(event)).isSameAs(outage);
    }

    private AuditEvent event() {
        return new AuditEvent(UUID.randomUUID(), UUID.randomUUID(), "PARAMETER_UPDATED", "SYSTEM_PARAMETER",
                "PRM-001", Map.of("value", 5), Map.of("value", 10), "127.0.0.1",
                UUID.randomUUID().toString(), Instant.parse("2026-10-14T00:00:00.123Z"));
    }
}
