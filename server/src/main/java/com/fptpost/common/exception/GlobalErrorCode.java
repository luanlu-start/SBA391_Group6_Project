package com.fptpost.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements ErrorCode {
    UNAUTHORIZED("SYS_UNAUTHORIZED", HttpStatus.UNAUTHORIZED),
    INVALID_REQUEST("SYS_INVALID_REQUEST", HttpStatus.BAD_REQUEST),
    FORBIDDEN("SYS_FORBIDDEN", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND("SYS_RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND),
    RESOURCE_ALREADY_EXISTS("SYS_RESOURCE_ALREADY_EXISTS", HttpStatus.CONFLICT),
    RESOURCE_VERSION_CONFLICT("SYS_VERSION_CONFLICT", HttpStatus.CONFLICT),
    METHOD_NOT_ALLOWED("SYS_METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE("SYS_NOT_ACCEPTABLE", HttpStatus.NOT_ACCEPTABLE),
    PAYLOAD_TOO_LARGE("SYS_PAYLOAD_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE("SYS_UNSUPPORTED_MEDIA_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    UPSTREAM_UNAVAILABLE("SYS_UPSTREAM_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_SERVER_ERROR("SYS_INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final HttpStatus httpStatus;

    public static GlobalErrorCode forStatus(int status) {
        return switch (status) {
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> RESOURCE_ALREADY_EXISTS;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 503 -> UPSTREAM_UNAVAILABLE;
            default -> status >= 500 ? INTERNAL_SERVER_ERROR : INVALID_REQUEST;
        };
    }
}
