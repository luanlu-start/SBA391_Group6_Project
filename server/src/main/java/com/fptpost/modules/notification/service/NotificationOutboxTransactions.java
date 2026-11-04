package com.fptpost.modules.notification.service;

import com.fptpost.modules.notification.config.NotificationOutboxProperties;
import com.fptpost.modules.notification.enums.*;
import com.fptpost.modules.notification.model.NotificationDelivery;
import com.fptpost.modules.notification.repository.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class NotificationOutboxTransactions {
    private final NotificationDeliveryRepository deliveries;
    private final NotificationOutboxProperties properties;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationDeliveryClaim> claimBatch(Set<NotificationChannel> channels) {
        if (channels.isEmpty()) return List.of();
        Instant now = clock.instant(), cutoff = now.minusSeconds(properties.getLeaseSeconds());
        var rows = deliveries.lockEligible(channels, NotificationDeliveryStatus.PENDING, NotificationDeliveryStatus.PROCESSING,
                now, cutoff, PageRequest.of(0, properties.getBatchSize()));
        var result = rows.stream().filter(row -> row.eligible(now, cutoff)).map(row -> {
            row.setStatus(NotificationDeliveryStatus.PROCESSING); row.setClaimedAt(now); row.setClaimToken(UUID.randomUUID());
            row.setAttempts(row.getAttempts() + 1);
            return new NotificationDeliveryClaim(row.getId(), row.getClaimToken(), row.getChannel(), row.getAttempts(), row.getEvent());
        }).toList();
        deliveries.flush();
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renew(NotificationDeliveryClaim claim) {
        return deliveries.lockById(claim.id()).filter(row -> owned(row, claim)).map(row -> {
            row.setClaimedAt(clock.instant()); return true;
        }).orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(NotificationDeliveryClaim claim, boolean skipped) {
        return deliveries.lockById(claim.id()).filter(row -> owned(row, claim)).map(row -> {
            row.setStatus(skipped ? NotificationDeliveryStatus.SKIPPED : NotificationDeliveryStatus.DELIVERED);
            row.setCompletedAt(clock.instant()); row.setClaimToken(null); row.setClaimedAt(null); row.setLastFailure(null); return true;
        }).orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void defer(NotificationDeliveryClaim claim) {
        deliveries.lockById(claim.id()).filter(row -> owned(row, claim)).ifPresent(row -> {
            row.setStatus(NotificationDeliveryStatus.PENDING); row.setClaimToken(null); row.setClaimedAt(null);
            row.setAttempts(Math.max(0, row.getAttempts() - 1)); row.setNextAttemptAt(clock.instant().plusSeconds(30));
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Boolean> retry(NotificationDeliveryClaim claim, int retryLimit, Exception failure) {
        return deliveries.lockById(claim.id()).filter(row -> owned(row, claim)).map(row -> {
            boolean exhausted = row.getAttempts() >= retryLimit + 1;
            row.setStatus(exhausted ? NotificationDeliveryStatus.FAILED : NotificationDeliveryStatus.PENDING);
            row.setClaimToken(null); row.setClaimedAt(null); row.setLastFailure(failure.getClass().getSimpleName());
            row.setNextAttemptAt(clock.instant().plusSeconds(retryDelay(row.getAttempts())));
            if (exhausted) row.setCompletedAt(clock.instant());
            return exhausted;
        });
    }

    private boolean owned(NotificationDelivery row, NotificationDeliveryClaim claim) {
        return row.getStatus() == NotificationDeliveryStatus.PROCESSING && claim.token().equals(row.getClaimToken());
    }
    private long retryDelay(int attempts) {
        long delay = (long) properties.getRetryBaseSeconds() * (1L << Math.min(20, Math.max(0, attempts - 1)));
        return Math.min(properties.getRetryMaxSeconds(), delay);
    }
}
