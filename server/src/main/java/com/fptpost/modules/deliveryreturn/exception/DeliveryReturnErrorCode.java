package com.fptpost.modules.deliveryreturn.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter @RequiredArgsConstructor
public enum DeliveryReturnErrorCode implements ErrorCode {
    RETURN_ACCESS_DENIED("RETURN_ACCESS_DENIED", HttpStatus.FORBIDDEN),
    RETURN_INVALID_STATUS("RETURN_INVALID_STATUS", HttpStatus.CONFLICT),
    RETURN_CONTACT_REQUIRED("RETURN_CONTACT_REQUIRED", HttpStatus.BAD_REQUEST),
    RETURN_EVIDENCE_REQUIRED("RETURN_EVIDENCE_REQUIRED", HttpStatus.BAD_REQUEST),
    RETURN_NOT_FOUND("RETURN_NOT_FOUND", HttpStatus.NOT_FOUND),
    RETURN_POLICY_UNAVAILABLE("RETURN_POLICY_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE),
    RETURN_CODE_INVALID("RETURN_CODE_INVALID", HttpStatus.BAD_REQUEST),
    RETURN_CODE_EXPIRED("RETURN_CODE_EXPIRED", HttpStatus.BAD_REQUEST),
    RETURN_CODE_LOCKED("RETURN_CODE_LOCKED", HttpStatus.TOO_MANY_REQUESTS),
    RETURN_CODE_COOLDOWN("RETURN_CODE_COOLDOWN", HttpStatus.TOO_MANY_REQUESTS);
    private final String code;
    private final HttpStatus httpStatus;
}
