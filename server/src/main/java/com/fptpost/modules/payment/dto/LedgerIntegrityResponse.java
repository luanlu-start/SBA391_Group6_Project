package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response kiểm tra tính toàn vẹn / bất biến sổ cái kép (UC-43, API-109, BR-N-039).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả kiểm tra bất biến sổ cái kép (Tổng Nợ = Tổng Có)")
public class LedgerIntegrityResponse {

    @Schema(description = "Toàn bộ sổ cái có cân bằng tuyệt đối (Tổng Nợ == Tổng Có và không có giao dịch lệch) hay không")
    private boolean balanced;

    @Schema(description = "Tổng số giao dịch sổ cái đã kiểm tra")
    private long totalTransactions;

    @Schema(description = "Tổng số dòng bút toán Nợ/Có đã kiểm tra")
    private long totalEntries;

    @Schema(description = "Tổng phát sinh Nợ (Debit) toàn sổ cái (VND)")
    private long totalDebit;

    @Schema(description = "Tổng phát sinh Có (Credit) toàn sổ cái (VND)")
    private long totalCredit;

    @Schema(description = "Độ lệch giữa Tổng Nợ và Tổng Có (VND)")
    private long discrepancyAmount;

    @Schema(description = "Số lượng giao dịch không cân bằng Nợ/Có")
    private int unbalancedTransactionCount;

    @Schema(description = "Danh sách mã giao dịch sổ cái bị mất cân bằng (nếu có)")
    private List<String> unbalancedTransactionCodes;

    @Schema(description = "Thời điểm kiểm tra")
    private Instant checkedAt;

    @Schema(description = "Thông điệp kết luận")
    private String message;
}
