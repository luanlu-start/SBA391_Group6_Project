package com.fptpost.modules.identity.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OtpChallengeResponse {
    UUID challengeId;
    String verificationChannel;
    String maskedTarget;
    Instant expiresAt;
    Instant resendAvailableAt;
}
