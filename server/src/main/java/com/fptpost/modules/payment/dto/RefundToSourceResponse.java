package com.fptpost.modules.payment.dto;

import com.fptpost.modules.payment.model.RefundKind;
import com.fptpost.modules.payment.model.RefundStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response kết quả yêu cầu hoàn tiền về nguồn VNPay (UC-37, API-091).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin yêu cầu hoàn tiền đơn hàng về nguồn VNPay")
public class RefundToSourceResponse {

    @Schema(description = "ID yêu cầu hoàn tiền")
    private Long id;

    @Schema(description = "ID đơn hàng")
    private Long orderId;

    @Schema(description = "Mã đơn hàng")
    private String orderCode;

    @Schema(description = "ID giao dịch thanh toán VNPay gốc")
    private Long paymentTxnId;

    @Schema(description = "Số tiền hoàn cho người gửi (VND)")
    private Long amount;

    @Schema(description = "Phí hủy đơn khấu trừ trả người giao (VND, nếu có)")
    private Long cancellationFee;

    @Schema(description = "Loại hoàn tiền (FULL / PARTIAL)")
    private RefundKind kind;

    @Schema(description = "Trạng thái yêu cầu hoàn tiền (PENDING / SUCCESS / FAILED)")
    private RefundStatus status;

    @Schema(description = "Mã tham chiếu hoàn tiền VNPay")
    private String vnpayRefundRef;

    @Schema(description = "Lý do yêu cầu hoàn tiền")
    private String reason;

    @Schema(description = "Thông báo kết quả xử lý")
    private String message;

    @Schema(description = "Thời điểm tạo yêu cầu")
    private Instant createdAt;
}
