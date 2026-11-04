package com.fptpost.modules.identity.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SessionResponse {
    UUID id;
    String deviceInfo;
    String ip;
    Instant createdAt;
    Instant expiresAt;
    boolean current;
}
