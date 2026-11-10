package com.fptpost.modules.moderation.dto.response;

import com.fptpost.modules.moderation.enums.ViolationCaseStatus;
import com.fptpost.modules.moderation.enums.ViolationReason;
import com.fptpost.modules.moderation.enums.ViolationTargetType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

/** Báo cáo của chính người dùng; không lộ hướng xử lý với người bị báo cáo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MyViolationReportResponse {
    UUID caseId;
    ViolationTargetType targetType;
    String targetLabel;
    ViolationReason reason;
    ViolationCaseStatus status;
    /** true khi báo cáo được gộp vào hồ sơ đã có người khác báo cáo trước. */
    boolean merged;
    Instant createdAt;
}
