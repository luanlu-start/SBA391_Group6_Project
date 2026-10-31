package com.fptpost.modules.catalog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Boolean isProhibited;
    private Boolean requiresReview;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
