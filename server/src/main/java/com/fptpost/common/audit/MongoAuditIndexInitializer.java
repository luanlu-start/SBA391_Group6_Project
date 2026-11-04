package com.fptpost.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Explicit indexes keep startup independent of Mongo availability and retry after an outage. */
@Component
public class MongoAuditIndexInitializer {
    private static final Logger log = LoggerFactory.getLogger(MongoAuditIndexInitializer.class);
    private final MongoTemplate mongoTemplate;
    private final AuditIndexProperties properties;

    public MongoAuditIndexInitializer(MongoTemplate mongoTemplate, AuditIndexProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "${audit.indexes.fixed-delay-ms:60000}",
            fixedDelayString = "${audit.indexes.fixed-delay-ms:60000}")
    public void scheduledEnsureIndexes() {
        if (!properties.isEnabled()) return;
        try {
            ensureIndexes();
        } catch (Exception failure) {
            // SQL capture/outbox remains available; do not expose connection details or payloads.
            log.warn("Audit index initialization deferred ({})", failure.getClass().getSimpleName());
        }
    }

    /** MongoDB treats an existing index with the same definition as an idempotent operation. */
    public void ensureIndexes() {
        var indexes = mongoTemplate.indexOps(MongoAuditDocument.class);
        boolean eventIdUnique = indexes.getIndexInfo().stream().anyMatch(index -> index.isUnique()
                && !index.isSparse() && index.getExpireAfter().isEmpty()
                && (index.getPartialFilterExpression() == null || index.getPartialFilterExpression().isBlank())
                && index.getIndexFields().size() == 1
                && "eventId".equals(index.getIndexFields().getFirst().getKey())
                && index.getIndexFields().getFirst().getDirection() == Sort.Direction.ASC);
        // Keep an equivalent legacy @Indexed index regardless of its generated name.
        if (!eventIdUnique) indexes.createIndex(new Index().on("eventId", Sort.Direction.ASC)
                .unique().named("audit_event_id_unique"));
        indexes.createIndex(new Index().on("at", Sort.Direction.DESC).on("eventId", Sort.Direction.DESC)
                .named("audit_at_event"));
        indexes.createIndex(new Index().on("actor", Sort.Direction.ASC).on("at", Sort.Direction.DESC)
                .on("eventId", Sort.Direction.DESC).named("audit_actor_at_event"));
        indexes.createIndex(new Index().on("action", Sort.Direction.ASC).on("at", Sort.Direction.DESC)
                .on("eventId", Sort.Direction.DESC).named("audit_action_at_event"));
        indexes.createIndex(new Index().on("entityType", Sort.Direction.ASC).on("entityId", Sort.Direction.ASC)
                .on("at", Sort.Direction.DESC).on("eventId", Sort.Direction.DESC).named("audit_entity_at_event"));
        indexes.createIndex(new Index().on("entityType", Sort.Direction.ASC).on("at", Sort.Direction.DESC)
                .on("eventId", Sort.Direction.DESC).named("audit_entity_type_at_event"));
        indexes.createIndex(new Index().on("entityId", Sort.Direction.ASC).on("at", Sort.Direction.DESC)
                .on("eventId", Sort.Direction.DESC).named("audit_entity_id_at_event"));
    }
}
