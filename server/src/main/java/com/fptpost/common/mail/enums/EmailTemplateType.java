package com.fptpost.common.mail.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum EmailTemplateType {
    VERIFY_EMAIL("verify-email", Set.of("fullName", "otp", "expirationMinutes")),
    RESET_PASSWORD("reset-password", Set.of("fullName", "otp", "expirationMinutes")),
    CONTACT_CHANGE("contact-change", Set.of("fullName", "otp", "expirationMinutes")),
    PASSWORD_CHANGED("password-changed", Set.of("fullName", "changedAt")),
    NOTIFICATION("notification", Set.of("title", "body"));

    private final String templateName;
    private final Set<String> requiredVariables;
}
