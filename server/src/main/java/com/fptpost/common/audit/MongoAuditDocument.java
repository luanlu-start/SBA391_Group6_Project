package com.fptpost.common.audit;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;

/** Capture is insert-only; only the internal retention job can purge expired records. */
@Document("audit_logs")
public record MongoAuditDocument(@Id String id, @Indexed(unique = true) String eventId,
                                 String actorType, @Field("actor") String actorPublicId, String action, String entityType,
                                 String entityId, Map<String, Object> before, Map<String, Object> after,
                                 String ip, String correlationId, Instant at) {
    static MongoAuditDocument from(AuditEvent event) {
        return new MongoAuditDocument(event.eventId().toString(), event.eventId().toString(), "USER",
                event.actorPublicId().toString(), event.action(), event.entityType(), event.entityId(),
                event.before(), event.after(), event.ip(), event.correlationId(), event.at());
    }
}
