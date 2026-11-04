package com.fptpost.modules.identity.dto.response;

import java.time.Instant;
import java.util.UUID;
import com.fptpost.modules.identity.enums.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AccountDataRequestResponse {
    UUID id;
    AccountRequestType type;
    AccountRequestStatus status;
    Instant requestedAt;
    Instant dueAt;
}
