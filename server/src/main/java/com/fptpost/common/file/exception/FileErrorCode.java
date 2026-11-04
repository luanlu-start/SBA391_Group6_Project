package com.fptpost.common.file.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.*;
import org.springframework.http.HttpStatus;

@Getter @RequiredArgsConstructor
public enum FileErrorCode implements ErrorCode {
    INVALID_IMAGE("FILE_INVALID_IMAGE", HttpStatus.BAD_REQUEST),
    TOO_LARGE("FILE_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE),
    STORAGE_UNAVAILABLE("FILE_STORAGE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    private final String code;
    private final HttpStatus httpStatus;
}
