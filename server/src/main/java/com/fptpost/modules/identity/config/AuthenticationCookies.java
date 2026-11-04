package com.fptpost.modules.identity.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuthenticationCookies {
    public static final String REFRESH_COOKIE = "fptpost_refresh";
    public static final String BROWSER_COOKIE = "fptpost_browser";
    private final AuthenticationProperties properties;

    public String issue(String token, boolean rememberMe) {
        return cookie(token, rememberMe ? Duration.ofDays(properties.getRefreshTtlDays()) : Duration.ofSeconds(-1));
    }

    public String clear() {
        return cookie("", Duration.ZERO);
    }

    public UUID resolveBrowserId(String value) {
        if (value != null && value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")) {
            return UUID.fromString(value);
        }
        return UUID.randomUUID();
    }

    public String issueBrowser(UUID browserId) {
        // Identifies this browser profile; this cookie does not authenticate requests.
        return ResponseCookie.from(BROWSER_COOKIE, browserId.toString()).httpOnly(true).secure(properties.isCookieSecure())
                .sameSite("Strict").path("/api/v1/auth").maxAge(Duration.ofDays(365)).build().toString();
    }

    private String cookie(String token, Duration age) {
        return ResponseCookie.from(REFRESH_COOKIE, token).httpOnly(true).secure(properties.isCookieSecure())
                .sameSite("Strict").path("/api/v1/auth").maxAge(age).build().toString();
    }
}
