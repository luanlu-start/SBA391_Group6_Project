package com.fptpost.modules.identity.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class KycReviewSearchRequest {
    @Size(max = 100) private String q;
    @Size(max = 20) private String status = "PENDING";
    private Boolean flagged;
    private Boolean overdue;
    @Min(0) @Max(100000) private int page;
    @Min(1) @Max(100) private int size = 20;
}
