package com.fptpost.modules.identity.dto.response;

import com.fptpost.modules.identity.enums.UserStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterResponse {
    UUID userId;
    UserStatus status;
    UUID challengeId;
    String verificationChannel;
    String maskedTarget;
    Instant expiresAt;
    Instant resendAvailableAt;
}
