package com.fptpost.modules.backoffice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AccountLockResponse {
    String reason;
    Instant lockedAt;
    Instant lockedUntil;
    boolean permanent;
    String lockedByName;
}
