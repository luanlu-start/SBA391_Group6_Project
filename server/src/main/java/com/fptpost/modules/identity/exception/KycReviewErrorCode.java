package com.fptpost.modules.identity.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter @RequiredArgsConstructor
public enum KycReviewErrorCode implements ErrorCode {
    NOT_FOUND("KYC_REVIEW_NOT_FOUND", HttpStatus.NOT_FOUND),
    VERSION_CONFLICT("KYC_REVIEW_VERSION_CONFLICT", HttpStatus.CONFLICT),
    ALREADY_DECIDED("KYC_REVIEW_ALREADY_DECIDED", HttpStatus.CONFLICT),
    SELF_FORBIDDEN("KYC_REVIEW_SELF_FORBIDDEN", HttpStatus.FORBIDDEN),
    ADMIN_REQUIRED("KYC_REVIEW_ADMIN_REQUIRED", HttpStatus.FORBIDDEN),
    INVALID_REASON("KYC_REVIEW_INVALID_REASON", HttpStatus.BAD_REQUEST),
    INVALID_DOCUMENT("KYC_REVIEW_INVALID_DOCUMENT", HttpStatus.BAD_REQUEST),
    APPLICANT_UNAVAILABLE("KYC_REVIEW_APPLICANT_UNAVAILABLE", HttpStatus.CONFLICT),
    IDENTITY_CONFLICT("KYC_REVIEW_IDENTITY_CONFLICT", HttpStatus.CONFLICT),
    INVALID_FILTER("KYC_REVIEW_INVALID_FILTER", HttpStatus.BAD_REQUEST),
    FILE_LINK_INVALID("KYC_REVIEW_FILE_LINK_INVALID", HttpStatus.FORBIDDEN),
    SLA_UNAVAILABLE("KYC_REVIEW_SLA_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final HttpStatus httpStatus;
}
