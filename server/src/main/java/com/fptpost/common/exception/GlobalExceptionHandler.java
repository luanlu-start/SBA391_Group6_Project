package com.fptpost.common.exception;

import com.fptpost.common.mail.exception.EmailDeliveryException;
import com.fptpost.common.response.ApiProblem;
import com.fptpost.common.response.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ProblemResponseFactory problems;

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiProblem> handleAppException(AppException exception, HttpServletRequest request) {
        log.warn("API failure code={}", exception.getErrorCode().getCode());
        return problems.response(exception.getErrorCode(), request, List.of());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiProblem> handleBindingErrors(BindException exception, HttpServletRequest request) {
        return problems.response(GlobalErrorCode.INVALID_REQUEST, request, violations(exception));
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ApiProblem> handleEmailFailure(HttpServletRequest request) {
        return problems.response(GlobalErrorCode.UPSTREAM_UNAVAILABLE, request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiProblem> handleGenericException(Exception exception, HttpServletRequest request) {
        log.error("API failure code={} exceptionType={}", GlobalErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                exception.getClass().getSimpleName());
        return problems.response(GlobalErrorCode.INTERNAL_SERVER_ERROR, request, List.of());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                              HttpStatusCode status, WebRequest webRequest) {
        HttpServletRequest request = ((ServletWebRequest) webRequest).getRequest();
        List<FieldViolation> errors = List.of();
        if (exception instanceof MethodArgumentNotValidException invalid) {
            errors = violations(invalid);
        } else if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            errors = List.of(problems.violation(mismatch.getName(), "TYPE_MISMATCH"));
        }
        var response = problems.response(GlobalErrorCode.forStatus(status.value()), status.value(), request, errors, headers);
        return new ResponseEntity<>(response.getBody(), response.getHeaders(), response.getStatusCode());
    }

    private List<FieldViolation> violations(BindException exception) {
        return exception.getBindingResult().getFieldErrors().stream().map(problems::violation)
                .sorted(Comparator.comparing(FieldViolation::getField).thenComparing(FieldViolation::getCode))
                .toList();
    }
}
