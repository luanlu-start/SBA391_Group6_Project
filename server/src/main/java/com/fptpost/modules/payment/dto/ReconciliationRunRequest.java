package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request chạy đối soát giao dịch thủ công (UC-43, API-107).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu chạy đối soát giao dịch theo ngày")
public class ReconciliationRunRequest {

    @Schema(description = "Ngày cần đối soát (YYYY-MM-DD, mặc định ngày hôm nay/hôm trước)", example = "2026-11-10")
    private LocalDate reportDate;
}
