package com.fptpost.modules.catalog.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ParameterHistoryRequest {
    @Min(0) int page = 0;
    @Min(1) @Max(100) int size = 20;
}
