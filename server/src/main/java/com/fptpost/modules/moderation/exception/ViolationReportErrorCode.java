package com.fptpost.modules.moderation.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ViolationReportErrorCode implements ErrorCode {
    TARGET_NOT_FOUND("REPORT_TARGET_NOT_FOUND", HttpStatus.NOT_FOUND),
    TARGET_FORBIDDEN("REPORT_TARGET_FORBIDDEN", HttpStatus.FORBIDDEN),
    SELF_REPORT("REPORT_SELF_FORBIDDEN", HttpStatus.BAD_REQUEST),
    EVIDENCE_LIMIT("REPORT_EVIDENCE_LIMIT", HttpStatus.BAD_REQUEST),
    CASE_NOT_FOUND("REPORT_CASE_NOT_FOUND", HttpStatus.NOT_FOUND),
    CASE_CLOSED("REPORT_CASE_CLOSED", HttpStatus.CONFLICT),
    FILTER_INVALID("REPORT_FILTER_INVALID", HttpStatus.BAD_REQUEST),
    RESPONDENT_FORBIDDEN("REPORT_RESPONDENT_FORBIDDEN", HttpStatus.FORBIDDEN);

    private final String code;
    private final HttpStatus httpStatus;
}
