package com.fptpost.modules.dispute.dto.response;

import com.fptpost.modules.dispute.enums.ComplaintOutcome;
import com.fptpost.modules.dispute.enums.ComplaintReason;
import com.fptpost.modules.dispute.enums.ComplaintRole;
import com.fptpost.modules.dispute.enums.ComplaintStatus;

import java.time.Instant;
import java.util.UUID;

public record ComplaintSummaryResponse(
        UUID id,
        String code,
        ComplaintStatus status,
        ComplaintReason reason,
        String orderCode,
        String productName,
        ComplaintRole openerRole,
        ComplaintPerson openedBy,
        ComplaintPerson assignee,
        ComplaintOutcome outcome,
        Instant createdAt,
        Instant firstResponseDueAt,
        Instant resolveDueAt,
        boolean responseOverdue,
        boolean resolutionOverdue,
        Instant appealDeadline) {
}
