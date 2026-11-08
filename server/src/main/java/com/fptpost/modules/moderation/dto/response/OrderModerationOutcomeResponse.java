package com.fptpost.modules.moderation.dto.response;

import com.fptpost.modules.moderation.enums.OrderModerationStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/** Kết quả kiểm duyệt người gửi được xem; không lộ từ khóa đã khớp. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderModerationOutcomeResponse {
    OrderModerationStatus status;
    Instant submittedAt;
    Instant reviewedAt;
    String rejectionReason;
}
