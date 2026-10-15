package com.fptpost;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.security.AuthenticationRateLimiter;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationRateLimiterTest {
    @Test
    void limitsEachPeerSeparatelyAndResetsOnNextMinute() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-09T12:00:00Z"));
        var limiter = new AuthenticationRateLimiter(new RegistrationProperties(), clock);
        for (int i = 0; i < 10; i++) limiter.check("peer-a");
        assertThatThrownBy(() -> limiter.check("peer-a")).isInstanceOf(AppException.class);
        assertThatCode(() -> limiter.check("peer-b")).doesNotThrowAnyException();
        when(clock.instant()).thenReturn(Instant.parse("2026-10-09T12:01:00Z"));
        assertThatCode(() -> limiter.check("peer-a")).doesNotThrowAnyException();
    }
}
