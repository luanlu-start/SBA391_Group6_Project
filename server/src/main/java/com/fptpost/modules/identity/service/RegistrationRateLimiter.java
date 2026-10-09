package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.exception.IdentityErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

@Component @RequiredArgsConstructor
public class RegistrationRateLimiter {
    private final RegistrationProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    public synchronized void check(String remoteAddress) {
        long minute = clock.instant().getEpochSecond() / 60;
        windows.entrySet().removeIf(e -> e.getValue().minute != minute);
        Window window = windows.get(remoteAddress);
        if (window == null) {
            if (windows.size() >= 10000) throw new AppException(IdentityErrorCode.AUTH_RATE_LIMITED);
            window = new Window(minute);
            windows.put(remoteAddress, window);
        }
        if (window.count >= properties.getRequestsPerMinute()) {
            throw new AppException(IdentityErrorCode.AUTH_RATE_LIMITED);
        }
        window.count++;
    }

    private static final class Window {
        final long minute;
        int count;
        Window(long minute) { this.minute = minute; }
    }
}
