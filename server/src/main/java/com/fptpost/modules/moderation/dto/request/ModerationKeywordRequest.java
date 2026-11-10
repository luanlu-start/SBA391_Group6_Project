package com.fptpost.modules.moderation.dto.request;

import com.fptpost.modules.moderation.enums.ModerationKeywordLevel;
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
public class ModerationKeywordRequest {

    @NotBlank
    @Size(min = 2, max = 100)
    String term;

    @NotNull
    ModerationKeywordLevel level;
}
