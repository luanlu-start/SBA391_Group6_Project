package com.fptpost.modules.moderation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ModerationPartyResponse {
    UUID publicId;
    String fullName;
    String email;
}
