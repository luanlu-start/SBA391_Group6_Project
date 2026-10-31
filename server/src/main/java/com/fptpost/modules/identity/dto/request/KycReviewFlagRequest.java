package com.fptpost.modules.identity.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class KycReviewFlagRequest {
    @NotNull @Min(0) private Long expectedVersion;
    @NotBlank @Size(max = 500) private String reason;
}
