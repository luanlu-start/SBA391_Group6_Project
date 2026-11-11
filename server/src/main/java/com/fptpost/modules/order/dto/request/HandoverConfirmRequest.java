package com.fptpost.modules.order.dto.request;

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

    @NotBlank(message = "Mã xác nhận bàn giao (QR Token) là bắt buộc")
    private String qrToken;

    private String meetingPoint;

    private String note;

    private BigDecimal latitude;

    private BigDecimal longitude;
}
