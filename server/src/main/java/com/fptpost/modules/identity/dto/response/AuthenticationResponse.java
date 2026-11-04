package com.fptpost.modules.identity.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationResponse {
    @ToString.Exclude String accessToken;
    String tokenType;
    Instant expiresAt;
    UserResponse user;
}
