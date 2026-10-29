package com.fptpost.modules.catalog.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ParameterHistoryResponse {
    UUID id;
    String oldValue;
    String newValue;
    UUID changedBy;
    Instant changedAt;
    long version;
}
