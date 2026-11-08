package com.fptpost.modules.dispute.dto.response;

import com.fptpost.modules.dispute.enums.ComplaintRole;

import java.time.Instant;

/** Một tin nhắn trong lịch sử chat của đơn, chỉ đọc cho kiểm duyệt viên (BR-F-045). */
public record ComplaintChatLine(ComplaintRole authorRole, String content, Instant createdAt) {
}
