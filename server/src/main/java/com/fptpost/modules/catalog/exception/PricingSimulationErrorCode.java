package com.fptpost.modules.catalog.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter @RequiredArgsConstructor
public enum PricingSimulationErrorCode implements ErrorCode {
    QUOTE_INVALID("PARAMETER_QUOTE_INVALID", HttpStatus.BAD_REQUEST),
    UNAVAILABLE("PARAMETER_SIMULATION_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final HttpStatus httpStatus;
}
