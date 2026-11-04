package com.fptpost.modules.audit.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuditLogResponse {
    String eventId;
    String actorType;
    String actorPublicId;
    String action;
    String entityType;
    String entityId;
    Map<String, Object> before;
    Map<String, Object> after;
    String ip;
    String correlationId;
    Instant at;
}
