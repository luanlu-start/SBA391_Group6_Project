package com.fptpost.modules.payment.dto;

import com.fptpost.modules.payment.model.EscrowPaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phản hồi thanh toán và ký quỹ đơn hàng (UC-35).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả xử lý thanh toán ký quỹ đơn hàng")
public class OrderPaymentResponse {

    @Schema(description = "ID đơn hàng", example = "1")
    private Long orderId;

    @Schema(description = "Mã đơn hàng", example = "ORD-TEST-001")
    private String orderCode;

    @Schema(description = "Phương thức thanh toán đã chọn (WALLET hoặc VNPAY)", example = "WALLET")
    private EscrowPaymentMethod paymentMethod;

    @Schema(description = "Số tiền thanh toán ký quỹ (VND)", example = "250000")
    private Long amount;

    @Schema(description = "Trạng thái xử lý: SUCCESS (hoàn tất ngay qua ví) hoặc PENDING_PAYMENT (chờ duyệt VNPay)", example = "SUCCESS")
    private String status;

    @Schema(description = "Đường dẫn thanh toán VNPay (khi paymentMethod là VNPAY)")
    private String paymentUrl;

    @Schema(description = "Mã tham chiếu giao dịch VNPay (TxnRef)")
    private String txnRef;

    @Schema(description = "Thông báo kết quả", example = "Ký quỹ đơn hàng bằng ví thành công")
    private String message;
}
