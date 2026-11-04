package com.fptpost.modules.chat.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {

    CHAT_ROOM_NOT_FOUND(
            "CHAT_ROOM_NOT_FOUND",
            HttpStatus.NOT_FOUND
    ),

    CHAT_ACCESS_DENIED(
            "CHAT_ACCESS_DENIED",
            HttpStatus.FORBIDDEN
    ),

    CHAT_ROOM_READ_ONLY(
            "CHAT_ROOM_READ_ONLY",
            HttpStatus.CONFLICT
    ),

    CHAT_ORDER_NOT_ASSIGNED(
            "CHAT_ORDER_NOT_ASSIGNED",
            HttpStatus.CONFLICT
    );

    private final String code;

    private final HttpStatus httpStatus;
}