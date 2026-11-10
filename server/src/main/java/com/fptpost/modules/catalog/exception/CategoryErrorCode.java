package com.fptpost.modules.catalog.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CategoryErrorCode implements ErrorCode {
    CATEGORY_NOT_FOUND("CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND),
    CATEGORY_CODE_ALREADY_EXISTS("CATEGORY_CODE_ALREADY_EXISTS", HttpStatus.BAD_REQUEST),
    CATEGORY_IN_USE_CANNOT_DELETE("CATEGORY_IN_USE_CANNOT_DELETE", HttpStatus.BAD_REQUEST);

    private final String code;
    private final HttpStatus httpStatus;
}
