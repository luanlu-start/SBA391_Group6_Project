package com.fptpost.modules.identity.security;

import com.fptpost.modules.identity.config.AuthenticationProperties;
import com.fptpost.modules.identity.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class LoginAttemptLimiter {
    private record Attempts(int count, Instant expiresAt, boolean locked) {
    }

    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final AuthenticationProperties properties;
    private final Clock identityClock;

    public void check(String ip) {
        Instant now = identityClock.instant();
        Attempts entry = attempts.get(ip);
        if (entry == null && attempts.size() >= 16384) {
            attempts.entrySet().removeIf(item -> !now.isBefore(item.getValue().expiresAt()));
            if (attempts.size() >= 16384)
                throw new IdentityException(IdentityErrorCode.AUTH_RATE_LIMITED, 60);
        }
        if (entry != null && entry.locked() && now.isBefore(entry.expiresAt())) {
            throw new IdentityException(IdentityErrorCode.LOGIN_LOCKED,
                    Math.max(1, (Duration.between(now, entry.expiresAt()).toMillis() + 999) / 1000));
        }
    }

    public void failure(String ip) {
        Instant now = identityClock.instant();
        if (attempts.size() >= 16384) {
            attempts.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
            if (!attempts.containsKey(ip) && attempts.size() >= 16384) return;
        }
        attempts.compute(ip, (key, entry) -> {
            int count = entry == null || !now.isBefore(entry.expiresAt()) ? 1 : entry.count() + 1;
            return new Attempts(count, now.plusSeconds(properties.getLoginLockMinutes() * 60L),
                    count >= properties.getMaxLoginAttempts());
        });
    }

    public void success(String ip) {
        attempts.remove(ip);
    }
}
