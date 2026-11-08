package com.fptpost.modules.dispute.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ComplaintErrorCode implements ErrorCode {
    NOT_FOUND("COMPLAINT_NOT_FOUND", HttpStatus.NOT_FOUND),
    FORBIDDEN("COMPLAINT_FORBIDDEN", HttpStatus.FORBIDDEN),
    PARTY_CONFLICT("COMPLAINT_PARTY_CONFLICT", HttpStatus.FORBIDDEN),
    STATE_INVALID("COMPLAINT_STATE_INVALID", HttpStatus.CONFLICT),
    NOT_ASSIGNED("COMPLAINT_NOT_ASSIGNED", HttpStatus.CONFLICT),
    RULING_INVALID("COMPLAINT_RULING_INVALID", HttpStatus.BAD_REQUEST),
    COMPENSATION_NOT_ALLOWED("COMPLAINT_COMPENSATION_NOT_ALLOWED", HttpStatus.BAD_REQUEST),
    APPEAL_CLOSED("COMPLAINT_APPEAL_CLOSED", HttpStatus.CONFLICT),
    REVIEWER_CONFLICT("COMPLAINT_REVIEWER_CONFLICT", HttpStatus.FORBIDDEN),
    MESSAGE_INVALID("COMPLAINT_MESSAGE_INVALID", HttpStatus.BAD_REQUEST),
    FILTER_INVALID("COMPLAINT_FILTER_INVALID", HttpStatus.BAD_REQUEST),
    ORDER_INVALID("COMPLAINT_ORDER_INVALID", HttpStatus.CONFLICT),
    ALREADY_OPEN("COMPLAINT_ALREADY_OPEN", HttpStatus.CONFLICT);

    private final String code;
    private final HttpStatus httpStatus;
}
