package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request yêu cầu hoàn tiền về nguồn VNPay (UC-37, API-091).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu hoàn tiền đơn hàng về nguồn thanh toán VNPay")
public class RefundToSourceRequest {

    @Size(max = 500, message = "Lý do hoàn tiền không được vượt quá 500 ký tự")
    @Schema(description = "Lý do yêu cầu hoàn tiền về nguồn VNPay", example = "Đơn hàng đã hủy, yêu cầu hoàn về thẻ/tài khoản VNPay gốc")
    private String reason;

    @Schema(description = "Cờ mô phỏng cổng VNPay từ chối hoàn (dùng cho kiểm thử kịch bản A1)", example = "false")
    private Boolean simulateProviderRejection;
}
