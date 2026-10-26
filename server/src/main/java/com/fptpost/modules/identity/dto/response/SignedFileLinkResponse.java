package com.fptpost.modules.identity.dto.response;

import java.time.Instant;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SignedFileLinkResponse {
    String url;
    Instant expiresAt;
}
