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
 * Request tạo yêu cầu rút tiền về ngân hàng (UC-40, API-097).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu rút tiền từ ví về tài khoản ngân hàng thụ hưởng")
public class WithdrawalCreationRequest {

    @NotNull(message = "Số tiền rút không được để trống")
    @Min(value = 50000, message = "Số tiền rút tối thiểu là 50,000 VND")
    @Schema(description = "Số tiền cần rút (VND, tối thiểu 50,000)", example = "100000")
    private Long amount;

    @NotBlank(message = "Tên ngân hàng không được để trống")
    @Size(max = 100, message = "Tên ngân hàng không được vượt quá 100 ký tự")
    @Schema(description = "Tên ngân hàng thụ hưởng", example = "Vietcombank")
    private String bankName;

    @NotBlank(message = "Số tài khoản không được để trống")
    @Size(max = 50, message = "Số tài khoản không được vượt quá 50 ký tự")
    @Schema(description = "Số tài khoản ngân hàng thụ hưởng", example = "0123456789")
    private String accountNumber;

    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    @Size(max = 100, message = "Tên chủ tài khoản không được vượt quá 100 ký tự")
    @Schema(description = "Họ và tên chủ tài khoản thụ hưởng", example = "NGUYEN VAN A")
    private String accountHolderName;
}
