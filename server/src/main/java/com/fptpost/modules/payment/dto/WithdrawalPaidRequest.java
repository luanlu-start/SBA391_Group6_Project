package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Mã tham chiếu chuyển khoản do Admin nhập sau khi chi thủ công")
public class WithdrawalPaidRequest {

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[A-Za-z0-9._/-]+", message = "WITHDRAWAL_REFERENCE_INVALID")
    @Schema(example = "FT26310123456789")
    private String reference;
}
