package com.fptpost.modules.audit.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuditErrorCode implements ErrorCode {
    AUDIT_INVALID_FILTER("AUDIT_INVALID_FILTER", HttpStatus.BAD_REQUEST),
    AUDIT_STORE_UNAVAILABLE("AUDIT_STORE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final HttpStatus httpStatus;
}
