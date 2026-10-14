package com.fptpost.common.audit;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** Short SQL transactions only; the MongoDB call never holds SQL locks. */
@Service
public class AuditOutboxTransactions {
    private final AuditOutboxRepository repository;
    private final AuditOutboxProperties properties;
    private final Clock clock;

    public AuditOutboxTransactions(AuditOutboxRepository repository, AuditOutboxProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<AuditOutboxClaim> claimBatch() {
        var now = clock.instant();
        var cutoff = now.minus(properties.getLeaseDuration());
        var rows = repository.lockEligible(now, cutoff, AuditOutboxEntity.Status.PENDING,
                AuditOutboxEntity.Status.PROCESSING, PageRequest.of(0, properties.getBatchSize()));
        // Recheck after the row lock is acquired; another instance may just have claimed this row.
        var claims = rows.stream().filter(row -> row.eligible(now, cutoff)).map(row -> row.claim(now)).toList();
        repository.flush();
        return claims;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean acknowledge(AuditOutboxClaim claim) {
        return repository.lockById(claim.id()).filter(row -> row.ownedBy(claim)).map(row -> {
            row.delivered(clock.instant());
            return true;
        }).orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean retry(AuditOutboxClaim claim, Exception failure) {
        return repository.lockById(claim.id()).filter(row -> row.ownedBy(claim)).map(row -> {
            row.retry(clock.instant().plus(retryDelay(row.getAttempts())), failure.getClass().getSimpleName());
            return true;
        }).orElse(false);
    }

    private Duration retryDelay(int attempts) {
        try {
            Duration delay = properties.getRetryBaseDelay().multipliedBy(1L << Math.min(30, Math.max(0, attempts - 1)));
            return delay.compareTo(properties.getRetryMaxDelay()) > 0 ? properties.getRetryMaxDelay() : delay;
        } catch (ArithmeticException overflow) {
            return properties.getRetryMaxDelay();
        }
    }
}
