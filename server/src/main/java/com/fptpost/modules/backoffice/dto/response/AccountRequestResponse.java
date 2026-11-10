package com.fptpost.modules.backoffice.dto.response;

import com.fptpost.modules.identity.enums.AccountRequestStatus;
import com.fptpost.modules.identity.enums.AccountRequestType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AccountRequestResponse {
    UUID id;
    AccountRequestType type;
    AccountRequestStatus status;
    String reason;
    Instant requestedAt;
    Instant dueAt;
    boolean overdue;
    UUID userPublicId;
    String userFullName;
    String userEmail;
    Instant processedAt;
    String resolutionNote;
}
