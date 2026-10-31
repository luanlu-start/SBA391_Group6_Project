package com.fptpost.modules.notification.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter @RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {
    INVALID_SETTINGS("NOTIFICATION_INVALID_SETTINGS", HttpStatus.BAD_REQUEST),
    NOT_FOUND("NOTIFICATION_NOT_FOUND", HttpStatus.NOT_FOUND),
    STORE_UNAVAILABLE("NOTIFICATION_STORE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE),
    CHANNEL_UNAVAILABLE("NOTIFICATION_CHANNEL_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final HttpStatus httpStatus;
}
