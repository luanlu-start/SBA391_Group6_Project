package com.fptpost.modules.audit.retention;

import com.fptpost.common.audit.MongoAuditDocument;
import com.fptpost.modules.catalog.service.ParameterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Internal-only housekeeping. AUDIT_KEEP is read from the current parameter snapshot on every run. */
@Service
public class AuditRetentionService {
    private static final Logger log = LoggerFactory.getLogger(AuditRetentionService.class);
    private final MongoTemplate mongoTemplate;
    private final ParameterService parameterService;
    private final AuditRetentionProperties properties;
    private final Clock clock;

    public AuditRetentionService(MongoTemplate mongoTemplate, ParameterService parameterService,
                                 AuditRetentionProperties properties, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.parameterService = parameterService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${audit.retention.initial-delay-ms:60000}",
            fixedDelayString = "${audit.retention.fixed-delay-ms:3600000}")
    public void scheduledPurge() {
        if (!properties.isEnabled()) return;
        try {
            purgeExpiredBatch();
        } catch (Exception failure) {
            // Leave remaining records intact and retry next run; no payload or credentials are logged.
            log.warn("Audit retention deferred ({})", failure.getClass().getSimpleName());
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RetentionResult purgeExpiredBatch() {
        long years = parameterService.getSnapshot().integer("AUDIT_KEEP");
        if (years < 1 || years > 999) {
            throw new IllegalStateException("AUDIT_KEEP cannot be represented safely for retention");
        }
        Instant cutoff = clock.instant().atZone(ZoneOffset.UTC).minusYears(years).toInstant();
        Query expired = Query.query(Criteria.where("at").lt(cutoff))
                .with(Sort.by(Sort.Order.asc("at"), Sort.Order.asc("eventId"))).limit(1);
        int removed = 0;
        while (removed < properties.getBatchMax()) {
            // Selection and removal are atomic for each document, including with concurrent worker instances.
            if (mongoTemplate.findAndRemove(expired, MongoAuditDocument.class) == null) break;
            removed++;
        }
        return new RetentionResult(cutoff, removed);
    }

    public record RetentionResult(Instant cutoff, int removed) { }
}
