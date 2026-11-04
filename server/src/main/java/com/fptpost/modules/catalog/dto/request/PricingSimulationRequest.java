package com.fptpost.modules.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PricingSimulationRequest {
    @NotNull @Valid
    private PricingQuoteRequest quote;
    @NotNull @Size(max = 109) @Builder.Default
    private Map<@NotBlank @Size(max = 64) String, @NotBlank @Size(max = 1000) String> overrides = new LinkedHashMap<>();
}
