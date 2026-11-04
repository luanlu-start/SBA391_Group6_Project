package com.fptpost.modules.catalog.dto.response;

import com.fptpost.modules.catalog.enums.ParameterValueType;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ParameterResponse {
    String prmId;
    String key;
    String group;
    String name;
    ParameterValueType valueType;
    String value;
    String unit;
    String description;
    long version;
    Instant effectiveFrom;
    UUID updatedBy;
}
