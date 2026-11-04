package com.fptpost.modules.payment.dto;

import com.fptpost.modules.payment.model.EscrowPaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu thanh toán và ký quỹ đơn hàng (UC-35).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body thanh toán ký quỹ đơn hàng")
public class OrderPaymentRequest {

    @NotNull(message = "Phương thức thanh toán là bắt buộc (WALLET hoặc VNPAY)")
    @Schema(description = "Hình thức thanh toán: WALLET (Ví nội bộ) hoặc VNPAY (Cổng VNPay)", example = "WALLET")
    private EscrowPaymentMethod paymentMethod;

    @Schema(description = "Mã ngân hàng nếu chọn thanh toán VNPay (NCB, VNPAYQR, VISA...)", example = "NCB")
    private String bankCode;

    @Schema(description = "Ngôn ngữ thanh toán VNPay (vn hoặc en)", example = "vn", defaultValue = "vn")
    private String language;
}
