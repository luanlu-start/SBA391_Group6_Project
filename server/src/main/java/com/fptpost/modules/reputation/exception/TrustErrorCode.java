package com.fptpost.modules.reputation.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TrustErrorCode implements ErrorCode {
    COURIER_PROFILE_MISSING("TRUST_COURIER_PROFILE_MISSING", HttpStatus.NOT_FOUND),
    FILTER_INVALID("TRUST_FILTER_INVALID", HttpStatus.BAD_REQUEST);

    private final String code;
    private final HttpStatus httpStatus;
}
