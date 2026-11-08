package com.fptpost.modules.reporting.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportingErrorCode implements ErrorCode {
    REPORT_RANGE_INVALID("REPORT_RANGE_INVALID", HttpStatus.BAD_REQUEST),
    REPORT_TYPE_INVALID("REPORT_TYPE_INVALID", HttpStatus.BAD_REQUEST);

    private final String code;
    private final HttpStatus httpStatus;
}
