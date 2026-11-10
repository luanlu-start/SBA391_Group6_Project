package com.fptpost.modules.payment.dto;

import com.fptpost.modules.payment.model.ReconciliationReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Response báo cáo đối soát giao dịch hằng ngày (UC-43, API-106, API-107).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Báo cáo đối soát giao dịch hằng ngày")
public class ReconciliationReportResponse {

    private Long id;
    private LocalDate reportDate;
    private int totalCount;
    private int matchedCount;
    private int mismatchCount;
    private ReconciliationReportStatus status;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ReconciliationItemResponse> items;
}
