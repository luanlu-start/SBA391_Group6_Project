package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Yêu cầu nạp tiền vào ví người dùng qua VNPay (UC-34).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body nạp tiền vào ví điện tử")
public class WalletTopupRequest {

    @NotNull(message = "Số tiền nạp không được để trống")
    @DecimalMin(value = "20000", message = "Số tiền nạp tối thiểu là 20,000 VND [PRM-056]")
    @DecimalMax(value = "10000000", message = "Số tiền nạp tối đa mỗi giao dịch là 10,000,000 VND [PRM-057]")
    @Schema(description = "Số tiền muốn nạp vào ví (VND)", example = "200000")
    private BigDecimal amount;

    @Schema(description = "Mã ngân hàng (tùy chọn: VNPAYQR, NCB, VISA...)", example = "NCB")
    private String bankCode;

    @Schema(description = "Ngôn ngữ hiển thị trên cổng VNPay (vn hoặc en)", example = "vn", defaultValue = "vn")
    private String language;
}
