package com.fptpost.modules.pricing.dto.request;

import com.fptpost.modules.pricing.enums.*;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class PromotionWriteRequest {
    @NotBlank @Size(max = 50) private String code;
    @NotNull private PromotionType type;
    @NotBlank @Size(max = 32) private String value;
    @NotNull private Instant validFrom;
    @NotNull private Instant validTo;
    @NotNull @Min(1) private Integer usageLimit;
    @NotNull private PromotionTarget target;
    @NotNull @Size(max = 100) private List<@NotNull UUID> targetUserIds;
    @NotNull private PromotionStatus status;
    @Min(0) private Long expectedVersion;
}
