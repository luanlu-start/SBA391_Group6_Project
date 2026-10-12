package com.fptpost.modules.identity.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AuthenticationCookies {
    public static final String REFRESH_COOKIE = "fptpost_refresh";
    private final AuthenticationProperties properties;

    public String issue(String token) {
        return cookie(token, Duration.ofDays(properties.getRefreshTtlDays()));
    }

    public String clear() {
        return cookie("", Duration.ZERO);
    }

    private String cookie(String token, Duration age) {
        return ResponseCookie.from(REFRESH_COOKIE, token).httpOnly(true).secure(properties.isCookieSecure())
                .sameSite("Strict").path("/api/v1/auth").maxAge(age).build().toString();
    }
}
