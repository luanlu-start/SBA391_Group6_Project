package com.fptpost.modules.identity.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class KycReviewVersionRequest {
    @NotNull @Min(0) private Long expectedVersion;
}
