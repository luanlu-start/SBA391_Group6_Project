package com.fptpost.modules.audit.mapper;

import com.fptpost.common.audit.AuditPayloadMasker;
import com.fptpost.common.audit.MongoAuditDocument;
import com.fptpost.modules.audit.dto.response.AuditLogResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

/** Reapply capture masking to protect responses containing older or externally inserted documents. */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditLogMapper {
    AuditPayloadMasker auditPayloadMasker;

    public AuditLogResponse toResponse(MongoAuditDocument document) {
        return AuditLogResponse.builder()
                .eventId(document.eventId()).actorType(document.actorType()).actorPublicId(document.actorPublicId())
                .action(document.action()).entityType(document.entityType()).entityId(document.entityId())
                .before(auditPayloadMasker.mask(document.before())).after(auditPayloadMasker.mask(document.after()))
                .ip(document.ip()).correlationId(document.correlationId()).at(document.at()).build();
    }
}
