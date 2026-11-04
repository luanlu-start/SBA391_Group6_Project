package com.fptpost.modules.catalog.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ParameterErrorCode implements ErrorCode {
    PARAMETER_NOT_FOUND("PARAMETER_NOT_FOUND", HttpStatus.NOT_FOUND),
    PARAMETER_INVALID_VALUE("PARAMETER_INVALID_VALUE", HttpStatus.BAD_REQUEST),
    PARAMETER_CONSTRAINT_VIOLATION("PARAMETER_CONSTRAINT_VIOLATION", HttpStatus.BAD_REQUEST),
    PARAMETER_VERSION_CONFLICT("PARAMETER_VERSION_CONFLICT", HttpStatus.CONFLICT);

    private final String code;
    private final HttpStatus httpStatus;
}
