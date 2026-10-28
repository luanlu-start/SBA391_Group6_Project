package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Phản hồi sao kê tài khoản ví theo tháng/năm (UC-38).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bảng sao kê biến động tài khoản ví theo kỳ")
public class WalletStatementResponse {

    @Schema(description = "ID người dùng sở hữu ví", example = "1")
    private Long userId;

    @Schema(description = "Kỳ sao kê (tháng/năm)", example = "10/2026")
    private String period;

    @Schema(description = "Số dư đầu kỳ (VND)", example = "500000")
    private Long startingBalance;

    @Schema(description = "Số dư cuối kỳ (VND)", example = "1500000")
    private Long endingBalance;

    @Schema(description = "Tổng tiền vào (Inflow) trong kỳ (VND)", example = "1200000")
    private Long totalInflow;

    @Schema(description = "Tổng tiền ra (Outflow) trong kỳ (VND)", example = "200000")
    private Long totalOutflow;

    @Schema(description = "Chênh lệch dòng tiền thuần = totalInflow - totalOutflow (VND)", example = "1000000")
    private Long netChange;

    @Schema(description = "Tổng số lượng giao dịch trong kỳ", example = "5")
    private Integer transactionCount;

    @Builder.Default
    @Schema(description = "Danh sách chi tiết các biến động giao dịch trong kỳ")
    private List<WalletTransactionResponse> transactions = new ArrayList<>();
}
