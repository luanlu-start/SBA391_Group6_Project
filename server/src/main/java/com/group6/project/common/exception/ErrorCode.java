package com.group6.project.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INTERNAL_SERVER_ERROR(500, "Internal server error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(400, "Invalid request payload or parameters", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(404, "Requested resource not found", HttpStatus.NOT_FOUND),
    RESOURCE_ALREADY_EXISTS(409, "Resource already exists", HttpStatus.CONFLICT),
    UNAUTHORIZED(401, "Authentication required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(403, "Access denied", HttpStatus.FORBIDDEN);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
