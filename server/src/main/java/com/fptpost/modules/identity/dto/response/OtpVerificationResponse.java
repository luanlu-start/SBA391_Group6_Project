package com.fptpost.modules.identity.dto.response;

import com.fptpost.modules.identity.enums.UserStatus;
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
public class OtpVerificationResponse {
    UUID userId;
    UserStatus status;
    String verificationChannel;
    Instant verifiedAt;
}
