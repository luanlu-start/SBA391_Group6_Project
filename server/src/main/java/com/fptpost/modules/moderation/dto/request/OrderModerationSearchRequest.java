package com.fptpost.modules.moderation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderModerationSearchRequest {

    @Size(max = 100)
    String q;

    @Builder.Default
    @Min(0)
    Integer page = 0;

    @Builder.Default
    @Min(1)
    @Max(100)
    Integer size = 20;
}
