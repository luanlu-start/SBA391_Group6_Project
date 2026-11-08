package com.fptpost.modules.jobs.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum JobErrorCode implements ErrorCode {
    JOB_NOT_FOUND("JOB_NOT_FOUND", HttpStatus.NOT_FOUND),
    JOB_RUNNING("JOB_RUNNING", HttpStatus.CONFLICT);

    private final String code;
    private final HttpStatus httpStatus;
}
