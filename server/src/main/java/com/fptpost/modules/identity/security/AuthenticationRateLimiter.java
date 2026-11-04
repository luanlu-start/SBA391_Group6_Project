package com.fptpost.modules.identity.security;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.exception.IdentityErrorCode;
import com.fptpost.modules.identity.exception.IdentityException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthenticationRateLimiter {
    private final RegistrationProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();
    private final Map<String, Instant> passwordRequests = new HashMap<>();

    public synchronized void checkPasswordReset(String identifier) {
        Instant now = clock.instant();
        int cooldown = properties.getOtpResendCooldownSeconds();
        passwordRequests.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().plusSeconds(cooldown)));
        Instant previous = passwordRequests.get(identifier);
        if (previous != null) {
            long milliseconds = Duration.between(now, previous.plusSeconds(cooldown)).toMillis();
            throw new IdentityException(IdentityErrorCode.OTP_RESEND_TOO_SOON, Math.max(1, (milliseconds + 999) / 1000));
        }
        if (passwordRequests.size() >= 10000) throw new IdentityException(IdentityErrorCode.AUTH_RATE_LIMITED, 60);
        // Count every valid identifier equally, including unknown or disabled accounts.
        passwordRequests.put(identifier, now);
    }

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
