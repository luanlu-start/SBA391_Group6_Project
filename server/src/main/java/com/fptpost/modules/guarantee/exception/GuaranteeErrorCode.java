package com.fptpost.modules.guarantee.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GuaranteeErrorCode implements ErrorCode {
    COMPENSATION_NOT_FOUND("COMPENSATION_NOT_FOUND", HttpStatus.NOT_FOUND),
    COMPENSATION_STATE_INVALID("COMPENSATION_STATE_INVALID", HttpStatus.CONFLICT),
    COMPENSATION_AMOUNT_INVALID("COMPENSATION_AMOUNT_INVALID", HttpStatus.BAD_REQUEST),
    COMPENSATION_SELF_FORBIDDEN("COMPENSATION_SELF_FORBIDDEN", HttpStatus.FORBIDDEN),
    COMPENSATION_NOTE_REQUIRED("COMPENSATION_NOTE_REQUIRED", HttpStatus.BAD_REQUEST),
    COMPENSATION_FILTER_INVALID("COMPENSATION_FILTER_INVALID", HttpStatus.BAD_REQUEST);

    private final String code;
    private final HttpStatus httpStatus;
}
