package com.fptpost.modules.identity.exception;

import com.fptpost.common.exception.ProblemResponseFactory;
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

@Order(0)
@RequiredArgsConstructor
@RestControllerAdvice(assignableTypes = AuthenticationController.class)
public class IdentityExceptionHandler {
    private final ProblemResponseFactory problems;
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiProblem> conflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        // Also handles simultaneous requests passing the initial existence checks.
        return problems.response(IdentityErrorCode.REGISTRATION_CONFLICT, request, List.of());
    }
}
