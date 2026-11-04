package com.fptpost.modules.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ParameterBatchUpdateRequest {
    @NotEmpty @Size(max = 109)
    Map<@NotBlank @Size(max = 64) String, @NotNull @Valid ParameterUpdateRequest> changes;
}
