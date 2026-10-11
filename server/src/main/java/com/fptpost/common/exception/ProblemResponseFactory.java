package com.fptpost.common.exception;

import com.fptpost.common.request.CorrelationIdFilter;
import com.fptpost.common.response.ApiProblem;
import com.fptpost.common.response.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProblemResponseFactory {
    private static final Locale VI = Locale.forLanguageTag("vi");
    private static final Set<String> CONSTRAINTS = Set.of("NotBlank", "NotNull", "NotEmpty", "Size",
            "Email", "Pattern", "Min", "Max", "DecimalMin", "DecimalMax", "Digits", "AssertTrue", "Past");
    private final MessageSource messageSource;

    public ApiProblem create(ErrorCode code, int status, HttpServletRequest request, List<FieldViolation> errors) {
        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setType(URI.create("https://fptpost.vn/errors/" + code.getCode().toLowerCase(Locale.ROOT).replace('_', '-')));
        detail.setTitle(message(code.getCode() + ".title", "Không thể xử lý yêu cầu"));
        detail.setDetail(message(code.getCode() + ".detail", "Yêu cầu không thể được xử lý. Vui lòng thử lại sau."));
        // Never copy query values, rejected inputs or exception messages into the response.
        return new ApiProblem(detail, code.getCode(), CorrelationIdFilter.getOrCreate(request), errors);
    }

    public ResponseEntity<ApiProblem> response(ErrorCode code, HttpServletRequest request, List<FieldViolation> errors) {
        return response(code, code.getHttpStatus().value(), request, errors, new HttpHeaders());
    }

    public ResponseEntity<ApiProblem> response(ErrorCode code, int status, HttpServletRequest request,
                                               List<FieldViolation> errors, HttpHeaders originalHeaders) {
        ApiProblem problem = create(code, status, request, errors);
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(originalHeaders);
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.setCacheControl("no-store");
        // CorrelationIdFilter owns the header; ResponseEntity would append a second value.
        return new ResponseEntity<>(problem, headers, status);
    }

    public FieldViolation violation(FieldError error) {
        if (error.isBindingFailure()) return violation(error.getField(), "TYPE_MISMATCH");
        String configuredCode = error.getDefaultMessage();
        // Only registered message keys become public codes; never echo arbitrary messages or rejected values.
        if (configuredCode != null && configuredCode.matches("[A-Z][A-Z0-9_]{0,63}")
                && messageSource.getMessage("validation." + configuredCode, null, null, VI) != null) {
            return violation(error.getField(), configuredCode);
        }
        String constraint = error.getCode();
        String code = constraint != null && CONSTRAINTS.contains(constraint)
                ? constraint.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT) : "INVALID_VALUE";
        return violation(error.getField(), code);
    }

    public FieldViolation violation(String field, String code) {
        return new FieldViolation(field, code, message("validation." + code, "Giá trị không hợp lệ."));
    }

    private String message(String key, String fallback) {
        return messageSource.getMessage(key, null, fallback, VI);
    }
}
