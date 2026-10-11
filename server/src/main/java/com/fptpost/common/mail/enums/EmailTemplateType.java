package com.fptpost.common.mail.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum EmailTemplateType {
    VERIFY_EMAIL("verify-email", Set.of("fullName", "otp", "expirationMinutes"));

    private final String templateName;
    private final Set<String> requiredVariables;
}
