package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Thông tin ví người dùng (UC-38).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết ví người dùng")
public class WalletResponse {

    @Schema(description = "ID ví", example = "1")
    private Long id;

    @Schema(description = "ID người dùng sở hữu ví", example = "1")
    private Long userId;

    @Schema(description = "Số dư khả dụng (VND)", example = "1500000")
    private Long available;

    @Schema(description = "Tiền đang giam giữ ký quỹ theo đơn (VND)", example = "250000")
    private Long escrowHeld;

    @Schema(description = "Tiền đang chờ duyệt rút (VND)", example = "0")
    private Long pendingWithdraw;

    @Schema(description = "Tổng tài sản trong ví = khả dụng + ký quỹ (VND)", example = "1750000")
    private Long totalBalance;

    @Schema(description = "Thời điểm cập nhật gần nhất")
    private Instant updatedAt;
}
