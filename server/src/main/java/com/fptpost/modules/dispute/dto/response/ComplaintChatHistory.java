package com.fptpost.modules.dispute.dto.response;

import java.util.List;

/** {@code available = false} khi kho chat tạm thời không đọc được; hồ sơ khiếu nại vẫn xử lý được. */
public record ComplaintChatHistory(boolean available, List<ComplaintChatLine> messages) {
}
