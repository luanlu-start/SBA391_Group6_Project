package com.fptpost.modules.backoffice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminUserTransactionResponse {
    String transactionCode;
    String type;
    long amount;
    String description;
    Instant createdAt;
}
