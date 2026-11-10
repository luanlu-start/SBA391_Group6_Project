package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request thêm tài khoản ngân hàng nhận tiền (UC-39, API-095).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu liên kết tài khoản ngân hàng nhận tiền")
public class BankAccountCreationRequest {

    @NotBlank(message = "Mã ngân hàng không được để trống")
    @Size(max = 30, message = "Mã ngân hàng không được vượt quá 30 ký tự")
    @Schema(description = "Mã ngân hàng (VD: VCB, TCB, MB, ACB, BIDV, VTB)", example = "VCB")
    private String bankCode;

    @Size(max = 100, message = "Tên ngân hàng không được vượt quá 100 ký tự")
    @Schema(description = "Tên ngân hàng hiển thị", example = "Vietcombank")
    private String bankName;

    @NotBlank(message = "Số tài khoản không được để trống")
    @Pattern(regexp = "^[0-9]{6,25}$", message = "Số tài khoản ngân hàng phải gồm từ 6 đến 25 chữ số")
    @Schema(description = "Số tài khoản ngân hàng", example = "0123456789")
    private String accountNumber;

    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    @Size(max = 100, message = "Tên chủ tài khoản không được vượt quá 100 ký tự")
    @Schema(description = "Họ và tên chủ tài khoản (phải khớp tên trên hồ sơ KYC)", example = "NGUYEN VAN A")
    private String holderName;
}
