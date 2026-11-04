package com.fptpost.modules.notification.service;

import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.notification.config.NotificationOutboxProperties;
import com.fptpost.modules.notification.enums.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Component @RequiredArgsConstructor @Slf4j
public class NotificationOutboxDispatcher {
    private final NotificationOutboxTransactions transactions;
    private final NotificationDeliveryAdapter adapter;
    private final NotificationOutboxProperties properties;
    private final NotificationCircuitBreaker circuit;
    private final ParameterService parameters;

    @Scheduled(scheduler = "notificationScheduler", fixedDelayString = "${notification.outbox.fixed-delay-ms:1000}", initialDelay = 3000)
    public void scheduledInbox() { if (properties.isEnabled()) safelyDispatch(Set.of(NotificationChannel.IN_APP)); }

    @Scheduled(scheduler = "notificationScheduler", fixedDelayString = "${notification.outbox.external-delay-ms:1000}", initialDelay = 3000)
    public void scheduledExternal() {
        if (!properties.isEnabled()) return;
        Set<NotificationChannel> allowed = EnumSet.noneOf(NotificationChannel.class);
        for (var channel : List.of(NotificationChannel.EMAIL, NotificationChannel.SMS)) if (circuit.available(channel)) allowed.add(channel);
        safelyDispatch(allowed);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DispatchResult dispatchBatch() { return dispatchChannels(EnumSet.allOf(NotificationChannel.class)); }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DispatchResult dispatchInbox() { return dispatchChannels(Set.of(NotificationChannel.IN_APP)); }

    private void safelyDispatch(Set<NotificationChannel> channels) {
        try { dispatchChannels(channels); }
        catch (RuntimeException failure) { log.warn("Notification dispatch deferred ({})", failure.getClass().getSimpleName()); }
    }

    private DispatchResult dispatchChannels(Set<NotificationChannel> channels) {
        if (channels.isEmpty()) return new DispatchResult(0, 0, 0, 0, 0);
        int retryLimit = Math.toIntExact(parameters.getSnapshot().integer("NOTIF_RETRY"));
        if (retryLimit < 0 || retryLimit > 20) throw new IllegalStateException("Invalid notification retry limit");
        var claims = transactions.claimBatch(channels);
        int delivered = 0, retried = 0, failed = 0, skipped = 0;
        for (var claim : claims) {
            if (!transactions.renew(claim)) continue;
            if (claim.channel() != NotificationChannel.IN_APP && !circuit.available(claim.channel())) {
                transactions.defer(claim); continue;
            }
            try {
                if (claim.attempt() > retryLimit + 1) throw new IllegalStateException("Notification attempts exhausted");
                boolean sent = adapter.deliver(claim);
                if (transactions.complete(claim, !sent)) {
                    if (sent) delivered++; else skipped++;
                    circuit.success(claim.channel());
                }
            } catch (Exception failure) {
                circuit.failure(claim.channel());
                var exhausted = transactions.retry(claim, retryLimit, failure);
                if (exhausted.isPresent()) {
                    if (exhausted.get()) {
                        failed++;
                        log.error("Notification delivery exhausted retries: delivery={}, channel={}", claim.id(), claim.channel());
                    } else retried++;
                }
            }
        }
        return new DispatchResult(claims.size(), delivered, retried, failed, skipped);
    }

    public record DispatchResult(int claimed, int delivered, int retried, int failed, int skipped) { }
}
