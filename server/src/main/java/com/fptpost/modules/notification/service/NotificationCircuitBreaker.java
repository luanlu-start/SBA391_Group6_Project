package com.fptpost.modules.notification.service;

import com.fptpost.modules.notification.enums.NotificationChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

/** Per-instance external-channel protection; delivery state and retry limits remain durable in SQL. */
@Component @RequiredArgsConstructor
public class NotificationCircuitBreaker {
    private final Clock clock;
    private final Map<NotificationChannel, Integer> failures = new EnumMap<>(NotificationChannel.class);
    private final Map<NotificationChannel, Instant> openUntil = new EnumMap<>(NotificationChannel.class);

    public synchronized boolean available(NotificationChannel channel) {
        return !clock.instant().isBefore(openUntil.getOrDefault(channel, Instant.MIN));
    }
    public synchronized void success(NotificationChannel channel) { failures.remove(channel); openUntil.remove(channel); }
    public synchronized void failure(NotificationChannel channel) {
        int count = failures.merge(channel, 1, Integer::sum);
        if (count >= 3) { openUntil.put(channel, clock.instant().plusSeconds(30)); failures.put(channel, 0); }
    }
}
