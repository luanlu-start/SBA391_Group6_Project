package com.fptpost.modules.backoffice.dto.response;

import com.fptpost.modules.identity.enums.UserStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminUserSummaryResponse {
    UUID publicId;
    String fullName;
    String email;
    String phone;
    List<String> roles;
    UserStatus status;
    boolean emailVerified;
    boolean phoneVerified;
    boolean fptBadge;
    Instant createdAt;
}
