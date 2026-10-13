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
    ORDER_INVALID("ORDER_INVALID", HttpStatus.BAD_REQUEST);

    private final String code;
    private final HttpStatus httpStatus;
}
