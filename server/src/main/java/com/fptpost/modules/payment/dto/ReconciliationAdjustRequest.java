package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request điều chỉnh dòng lệch bằng bút toán điều chỉnh (UC-43, API-108, BR-F-083).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu tạo bút toán điều chỉnh cho dòng giao dịch lệch")
public class ReconciliationAdjustRequest {

    @NotBlank(message = "Tài khoản ghi Nợ không được để trống")
    @Size(max = 64, message = "Mã tài khoản ghi Nợ tối đa 64 ký tự")
    @Schema(description = "Mã tài khoản ghi Nợ (Debit)", example = "VNPAY_CLEARING")
    private String debitAccount;

    @NotBlank(message = "Tài khoản ghi Có không được để trống")
    @Size(max = 64, message = "Mã tài khoản ghi Có tối đa 64 ký tự")
    @Schema(description = "Mã tài khoản ghi Có (Credit)", example = "RECONCILIATION_SUSPENSE")
    private String creditAccount;

    @NotNull(message = "Số tiền điều chỉnh không được để trống")
    @Min(value = 1, message = "Số tiền điều chỉnh phải lớn hơn 0")
    @Schema(description = "Số tiền bút toán điều chỉnh (VND)", example = "100000")
    private Long amount;

    @NotBlank(message = "Ghi chú lý do điều chỉnh không được để trống")
    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    @Schema(description = "Lý do / diễn giải bút toán điều chỉnh", example = "Điều chỉnh giao dịch VNPay thành công nhưng thiếu bút toán sổ cái")
    private String note;
}
