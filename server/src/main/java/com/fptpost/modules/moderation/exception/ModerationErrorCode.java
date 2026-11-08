package com.fptpost.modules.moderation.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ModerationErrorCode implements ErrorCode {
    ORDER_MODERATION_NOT_FOUND("ORDER_MODERATION_NOT_FOUND", HttpStatus.NOT_FOUND),
    ORDER_MODERATION_ALREADY_DECIDED("ORDER_MODERATION_ALREADY_DECIDED", HttpStatus.CONFLICT),
    ORDER_MODERATION_SELF_FORBIDDEN("ORDER_MODERATION_SELF_FORBIDDEN", HttpStatus.FORBIDDEN),
    ORDER_MODERATION_ACCESS_DENIED("ORDER_MODERATION_ACCESS_DENIED", HttpStatus.FORBIDDEN),
    MODERATION_KEYWORD_INVALID("MODERATION_KEYWORD_INVALID", HttpStatus.BAD_REQUEST),
    MODERATION_KEYWORD_DUPLICATED("MODERATION_KEYWORD_DUPLICATED", HttpStatus.CONFLICT),
    MODERATION_KEYWORD_NOT_FOUND("MODERATION_KEYWORD_NOT_FOUND", HttpStatus.NOT_FOUND);

    private final String code;
    private final HttpStatus httpStatus;
}
