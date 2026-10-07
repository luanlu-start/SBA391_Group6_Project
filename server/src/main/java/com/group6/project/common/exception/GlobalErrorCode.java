package com.group6.project.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements ErrorCode {
    SUCCESS(1000, "Success", HttpStatus.OK),
    UNAUTHORIZED(1001, "Authentication required", HttpStatus.UNAUTHORIZED),
    INVALID_REQUEST(1002, "Invalid request payload or parameters", HttpStatus.BAD_REQUEST),
    FORBIDDEN(1003, "Access denied", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(1004, "Requested resource not found", HttpStatus.NOT_FOUND),
    RESOURCE_ALREADY_EXISTS(1005, "Resource already exists", HttpStatus.CONFLICT),
    INTERNAL_SERVER_ERROR(9999, "An unexpected server error occurred", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}
