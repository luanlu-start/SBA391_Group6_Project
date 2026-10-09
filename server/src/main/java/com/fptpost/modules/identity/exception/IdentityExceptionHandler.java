package com.fptpost.modules.identity.exception;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.controller.AuthenticationController;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = AuthenticationController.class)
public class IdentityExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException exception) {
        // Also handles simultaneous requests passing the initial existence checks.
        return ResponseEntity.status(409).body(ApiResponse.error(IdentityErrorCode.REGISTRATION_CONFLICT));
    }
}
