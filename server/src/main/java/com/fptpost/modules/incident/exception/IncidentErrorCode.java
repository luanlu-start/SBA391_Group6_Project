package com.fptpost.modules.incident.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter @RequiredArgsConstructor
public enum IncidentErrorCode implements ErrorCode {
    INCIDENT_INVALID_STATUS("INCIDENT_INVALID_STATUS", HttpStatus.CONFLICT),
    INCIDENT_ACCESS_DENIED("INCIDENT_ACCESS_DENIED", HttpStatus.FORBIDDEN),
    INCIDENT_NOT_FOUND("INCIDENT_NOT_FOUND", HttpStatus.NOT_FOUND);
    private final String code;
    private final HttpStatus httpStatus;
}
