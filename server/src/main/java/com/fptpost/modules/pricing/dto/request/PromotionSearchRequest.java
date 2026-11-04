package com.fptpost.modules.pricing.dto.request;

import com.fptpost.modules.pricing.enums.PromotionEffectiveStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PromotionSearchRequest {
    @Size(max = 50) private String q;
    private PromotionEffectiveStatus status;
    @Min(0) @Max(100000) private int page = 0;
    @Min(1) @Max(100) private int size = 20;
}
