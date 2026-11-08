package com.fptpost.modules.moderation.dto.response;

import com.fptpost.modules.moderation.enums.ViolationAction;
import com.fptpost.modules.moderation.enums.ViolationCaseStatus;
import com.fptpost.modules.moderation.enums.ViolationReason;
import com.fptpost.modules.moderation.enums.ViolationTargetType;
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
public class ViolationCaseResponse {
    UUID id;
    ViolationTargetType targetType;
    String targetId;
    String targetLabel;
    Long relatedOrderId;
    ViolationParty respondent;
    /** Số lần người bị báo cáo đã bị cảnh cáo hoặc đề nghị khóa trước đây. */
    long respondentPriorViolations;
    ViolationReason latestReason;
    int reportCount;
    ViolationCaseStatus status;
    ViolationAction resolution;
    String resolutionNote;
    ViolationParty handledBy;
    Instant handledAt;
    Instant createdAt;
    Instant updatedAt;
    boolean canResolve;
    /** Chỉ có ở API chi tiết. */
    List<ViolationReportItemResponse> reports;
}
