package com.fptpost.modules.payment.dto;

import com.fptpost.modules.payment.model.LedgerTransactionStatus;
import com.fptpost.modules.payment.model.LedgerTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Phản hồi chi tiết một biến động giao dịch trong ví (UC-38).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết một biến động giao dịch ví")
public class WalletTransactionResponse {

    @Schema(description = "Mã chứng từ sổ cái", example = "LTX-TOPUP-ABC12345")
    private String transactionCode;

    @Schema(description = "Loại giao dịch", example = "WALLET_TOPUP")
    private LedgerTransactionType type;

    @Schema(description = "Số tiền giao dịch (VND)", example = "200000")
    private Long amount;

    @Schema(description = "Trạng thái giao dịch", example = "COMPLETED")
    private LedgerTransactionStatus status;

    @Schema(description = "Chiều biến động số dư đối với ví người dùng: IN (cộng tiền vào ví) hoặc OUT (trừ tiền khỏi ví)", example = "IN")
    private String balanceDirection;

    @Schema(description = "Mô tả nội dung giao dịch", example = "Nạp tiền vào ví qua VNPay")
    private String description;

    @Schema(description = "ID đơn hàng liên quan nếu có", example = "10")
    private Long orderId;

    @Schema(description = "Loại tham chiếu đối tượng", example = "PAYMENT_TRANSACTION")
    private String refType;

    @Schema(description = "Mã tham chiếu đối tượng (TxnRef hoặc EscrowRef)", example = "TOPUP_1_1729753200000")
    private String refId;

    @Schema(description = "Thời gian ghi nhận giao dịch")
    private Instant createdAt;
}
