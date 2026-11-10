package com.fptpost.modules.dispute.dto.request;

import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.modules.dispute.enums.ComplaintReason;

import java.util.List;

/**
 * Dữ liệu tạo khiếu nại đã qua kiểm tra của UC-50 (thời hạn, mô tả, bằng chứng).
 *
 * @param openedBy id nội bộ của người gửi hoặc người giao của đơn
 * @param evidence ảnh đã qua {@code ImageValidator}
 */
public record OpenComplaintCommand(Long orderId, Long openedBy, ComplaintReason reason, String description,
                                   List<ValidatedImage> evidence) {
}
