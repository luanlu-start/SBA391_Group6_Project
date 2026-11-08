package com.fptpost.modules.relay.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoverConfirmRequest {

    @NotBlank(message = "Mã xác thực bàn giao không được để trống")
    private String tokenCode;

    private String photoUrl;

    private String notes;

    private BigDecimal latitude;

    private BigDecimal longitude;
}
