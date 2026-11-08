package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lý do từ chối hoặc nguyên nhân chi thất bại")
public class WithdrawalReasonRequest {

    @NotBlank
    @Size(max = 500)
    private String reason;
}
