package com.fptpost.modules.identity.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter @RequiredArgsConstructor
public enum IdentityErrorCode implements ErrorCode {
    EMAIL_ALREADY_USED("AUTH_EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT),
    PHONE_ALREADY_USED("AUTH_PHONE_ALREADY_EXISTS", HttpStatus.CONFLICT),
    PASSWORD_INVALID("AUTH_PASSWORD_INVALID", HttpStatus.BAD_REQUEST),
    POLICY_CHANGED("AUTH_POLICY_CHANGED", HttpStatus.CONFLICT),
    REGISTRATION_NOT_ALLOWED("AUTH_REGISTRATION_BLOCKED", HttpStatus.FORBIDDEN),
    OTP_DELIVERY_FAILED("AUTH_OTP_DELIVERY_FAILED", HttpStatus.SERVICE_UNAVAILABLE),
    REGISTRATION_CONFLICT("AUTH_REGISTRATION_CONFLICT", HttpStatus.CONFLICT),
    AUTH_RATE_LIMITED("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final HttpStatus httpStatus;
}
