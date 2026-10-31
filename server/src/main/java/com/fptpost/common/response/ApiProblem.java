package com.fptpost.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.springframework.http.ProblemDetail;

import java.util.List;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiProblem", description = "Problem Details với mã nghiệp vụ và mã theo dõi request")
public class ApiProblem extends ProblemDetail {
    @Schema(example = "AUTH_EMAIL_ALREADY_EXISTS")
    private final String code;
    @Schema(example = "7f3c9a52-1d4e-4b8a-9c21-0a6e5d3f7b10", format = "uuid")
    private final String correlationId;
    private final List<FieldViolation> errors;

    public ApiProblem(ProblemDetail detail, String code, String correlationId, List<FieldViolation> errors) {
        super(detail);
        this.code = code;
        this.correlationId = correlationId;
        this.errors = errors == null || errors.isEmpty() ? null : List.copyOf(errors);
    }
}
