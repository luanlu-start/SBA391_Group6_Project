package com.fptpost.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.request.CorrelationIdFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditOutboxService {
    private final AuditOutboxRepository repository;
    private final ObjectMapper mapper;
    private final AuditPayloadMasker masker;
    private final AuditOutboxProperties properties;
    private final Clock clock;

    public AuditOutboxService(AuditOutboxRepository repository, ObjectMapper mapper, AuditPayloadMasker masker,
                              AuditOutboxProperties properties, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.masker = masker;
        this.properties = properties;
        this.clock = clock;
    }

    /** Business state, history and this enqueue must commit or roll back together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String action, String entityType, String entityId,
                        Map<String, Object> before, Map<String, Object> after) {
        requireLabel(action, "action", 100);
        requireLabel(entityType, "entityType", 100);
        requireLabel(entityId, "entityId", 200);
        UUID actor = trustedActor();
        String ip = null;
        String correlationId = UUID.randomUUID().toString();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            ip = attributes.getRequest().getRemoteAddr(); // Do not trust client-controlled forwarding headers.
            correlationId = CorrelationIdFilter.getOrCreate(attributes.getRequest());
        }
        var now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        var event = new AuditEvent(UUID.randomUUID(), actor, action, entityType, entityId,
                masker.mask(before), masker.mask(after), ip, correlationId, now);
        try {
            String payload = mapper.writeValueAsString(event);
            if (payload.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
                throw new IllegalArgumentException("Audit event exceeds the payload limit");
            }
            repository.save(AuditOutboxEntity.pending(event.eventId(), payload, now));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Audit event cannot be serialized", exception);
        }
    }

    private UUID trustedActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Audit capture requires an authenticated JWT actor");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("Audit JWT subject must be a user public UUID", exception);
        }
    }

    private void requireLabel(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("Invalid audit " + field);
        }
    }
}
