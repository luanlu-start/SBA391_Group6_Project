package com.fptpost.modules.identity.exception;

import com.fptpost.common.exception.ProblemResponseFactory;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.response.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import com.fptpost.modules.identity.controller.AuthenticationController;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

import org.springframework.http.HttpHeaders;
import com.fptpost.modules.identity.config.AuthenticationCookies;

@Order(0)
@RequiredArgsConstructor
@RestControllerAdvice(assignableTypes = AuthenticationController.class)
public class IdentityExceptionHandler {
    private final ProblemResponseFactory problems;
    private final AuthenticationCookies cookies;

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiProblem> appFailure(AppException exception, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (exception instanceof IdentityException identity && identity.getRetryAfterSeconds() > 0) {
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(identity.getRetryAfterSeconds()));
        }
        if (request.getRequestURI().substring(request.getContextPath().length()).equals("/api/v1/auth/refresh")) {
            headers.set(HttpHeaders.SET_COOKIE, cookies.clear());
        }
        ResponseEntity<ApiProblem> response = problems.response(exception.getErrorCode(),
                exception.getErrorCode().getHttpStatus().value(), request, List.of(), headers);
        if (exception instanceof IdentityException identity && identity.getVerification() != null) {
            response.getBody().setProperty("verification", identity.getVerification());
        }
        return response;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiProblem> conflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        // Also handles simultaneous requests passing the initial existence checks.
        return problems.response(IdentityErrorCode.REGISTRATION_CONFLICT, request, List.of());
    }
}
