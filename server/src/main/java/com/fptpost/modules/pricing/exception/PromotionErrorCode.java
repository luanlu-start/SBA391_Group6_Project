package com.fptpost.modules.pricing.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter @RequiredArgsConstructor
public enum PromotionErrorCode implements ErrorCode {
    NOT_FOUND("PROMOTION_NOT_FOUND", HttpStatus.NOT_FOUND),
    CODE_ALREADY_EXISTS("PROMOTION_CODE_ALREADY_EXISTS", HttpStatus.CONFLICT),
    VERSION_CONFLICT("PROMOTION_VERSION_CONFLICT", HttpStatus.CONFLICT),
    INVALID_VALUE("PROMOTION_INVALID_VALUE", HttpStatus.BAD_REQUEST),
    CODE_IMMUTABLE("PROMOTION_CODE_IMMUTABLE", HttpStatus.BAD_REQUEST),
    TARGET_INVALID("PROMOTION_TARGET_INVALID", HttpStatus.BAD_REQUEST),
    USAGE_LIMIT_BELOW_USED("PROMOTION_USAGE_LIMIT_BELOW_USED", HttpStatus.BAD_REQUEST);

    private final String code;
    private final HttpStatus httpStatus;
}
