package com.fptpost.modules.moderation.dto.request;

import com.fptpost.modules.moderation.enums.ViolationAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ViolationResolveRequest {

    @NotNull
    ViolationAction action;

    @NotBlank
    @Size(max = 500)
    String note;
}
