package com.fptpost.modules.backoffice.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserAdministrationErrorCode implements ErrorCode {
    USER_NOT_FOUND("ADMIN_USER_NOT_FOUND", HttpStatus.NOT_FOUND),
    FILTER_INVALID("ADMIN_USER_FILTER_INVALID", HttpStatus.BAD_REQUEST),
    SELF_FORBIDDEN("ADMIN_USER_SELF_FORBIDDEN", HttpStatus.FORBIDDEN),
    ROLES_INVALID("ADMIN_USER_ROLES_INVALID", HttpStatus.BAD_REQUEST),
    COURIER_PROFILE_REQUIRED("ADMIN_USER_COURIER_PROFILE_REQUIRED", HttpStatus.CONFLICT),
    LAST_ADMIN("ADMIN_USER_LAST_ADMIN", HttpStatus.CONFLICT),
    USER_DELETED("ADMIN_USER_DELETED", HttpStatus.CONFLICT),
    STAFF_EMAIL_TAKEN("ADMIN_STAFF_EMAIL_TAKEN", HttpStatus.CONFLICT),
    STAFF_PHONE_TAKEN("ADMIN_STAFF_PHONE_TAKEN", HttpStatus.CONFLICT),
    ACCOUNT_REQUEST_NOT_FOUND("ACCOUNT_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND),
    ACCOUNT_REQUEST_ALREADY_PROCESSED("ACCOUNT_REQUEST_ALREADY_PROCESSED", HttpStatus.CONFLICT),
    ACCOUNT_DELETION_BLOCKED("ACCOUNT_DELETION_BLOCKED", HttpStatus.CONFLICT),
    ACCOUNT_EXPORT_NOT_READY("ACCOUNT_EXPORT_NOT_READY", HttpStatus.CONFLICT);

    private final String code;
    private final HttpStatus httpStatus;
}
