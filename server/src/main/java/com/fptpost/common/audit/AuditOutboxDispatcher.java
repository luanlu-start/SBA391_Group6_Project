package com.fptpost.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuditOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AuditOutboxDispatcher.class);
    private final AuditOutboxTransactions transactions;
    private final MongoAuditSink sink;
    private final ObjectMapper mapper;
    private final AuditOutboxProperties properties;

    public AuditOutboxDispatcher(AuditOutboxTransactions transactions, MongoAuditSink sink,
                                 ObjectMapper mapper, AuditOutboxProperties properties) {
        this.transactions = transactions;
        this.sink = sink;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${audit.outbox.fixed-delay-ms:2000}")
    public void scheduledDispatch() {
        if (!properties.isEnabled()) return;
        try {
            dispatchBatch();
        } catch (Exception failure) {
            // Keep payloads/credentials out of application logs; the SQL lease enables recovery.
            log.warn("Audit outbox dispatch deferred ({})", failure.getClass().getSimpleName());
        }
    }

    /** Explicit entry point for tests/operations; scheduling can be disabled independently. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DispatchResult dispatchBatch() {
        var claims = transactions.claimBatch();
        int delivered = 0;
        int retried = 0;
        for (var claim : claims) {
            try {
                var event = mapper.readValue(claim.payload(), AuditEvent.class);
                if (!claim.eventId().equals(event.eventId())) throw new IllegalStateException("Outbox event ID mismatch");
                sink.append(event);
                if (transactions.acknowledge(claim)) delivered++;
            } catch (Exception failure) {
                if (transactions.retry(claim, failure)) retried++;
            }
        }
        return new DispatchResult(claims.size(), delivered, retried);
    }

    public record DispatchResult(int claimed, int delivered, int retried) { }
}
