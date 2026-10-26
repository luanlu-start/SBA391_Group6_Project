package com.fptpost.modules.order.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND),
    ORDER_CODE_ALREADY_EXISTS("ORDER_CODE_ALREADY_EXISTS", HttpStatus.CONFLICT),
    ORDER_INVALID("ORDER_INVALID", HttpStatus.BAD_REQUEST),
    INVALID_IMAGE_FILE("INVALID_IMAGE_FILE", HttpStatus.BAD_REQUEST),
    FILE_UPLOAD_FAILED("FILE_UPLOAD_FAILED", HttpStatus.INTERNAL_SERVER_ERROR),
    ORDER_CANNOT_BE_EDITED("ORDER_CANNOT_BE_EDITED", HttpStatus.BAD_REQUEST),
    ORDER_ACCESS_DENIED("ORDER_ACCESS_DENIED", HttpStatus.FORBIDDEN);

    private final String code;
    private final HttpStatus httpStatus;
}
