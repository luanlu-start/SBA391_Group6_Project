package com.fptpost.modules.dispute.event;

import com.fptpost.modules.dispute.enums.ComplaintOutcome;

import java.time.Instant;
import java.util.UUID;

/**
 * Phát trong cùng giao dịch khi khiếu nại có phán quyết cuối cùng và tiền ký quỹ đã được xử lý.
 * Bồi thường từ Quỹ bảo đảm (UC-52) và điểm uy tín (UC-49) đọc sự kiện này.
 */
public record ComplaintClosedEvent(Long complaintId, UUID complaintPublicId, Long orderId, Long senderId, Long courierId,
                                   ComplaintOutcome outcome, long compensationAmount, Instant ruledAt, Instant closedAt) {
}
