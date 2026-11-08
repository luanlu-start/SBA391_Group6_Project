package com.fptpost.modules.backoffice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminUserOrderResponse {
    Long id;
    String orderCode;
    String productName;
    String status;
    /** SENDER hoặc COURIER: vai trò của người dùng trong đơn. */
    String participation;
    Instant createdAt;
}
