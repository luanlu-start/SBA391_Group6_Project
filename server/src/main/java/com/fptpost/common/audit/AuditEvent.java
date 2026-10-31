package com.fptpost.common.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** The durable, masked snapshot captured inside the business SQL transaction. */
public record AuditEvent(UUID eventId, UUID actorPublicId, String action, String entityType,
                         String entityId, Map<String, Object> before, Map<String, Object> after,
                         String ip, String correlationId, Instant at) {
}
