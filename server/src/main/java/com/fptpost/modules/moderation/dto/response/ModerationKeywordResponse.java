package com.fptpost.modules.moderation.dto.response;

import com.fptpost.modules.moderation.enums.ModerationKeywordLevel;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ModerationKeywordResponse {
    Long id;
    String term;
    ModerationKeywordLevel level;
    Instant createdAt;
}
