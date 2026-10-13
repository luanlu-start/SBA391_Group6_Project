package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request payload for creating a VNPay payment redirect URL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for VNPay payment URL creation")
public class PaymentCreationRequest {

    @NotBlank(message = "Order code is required")
    @Schema(description = "Merchant internal order code / transaction reference", example = "ORD-20261013-001")
    private String orderCode;

    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "1000", message = "Amount must be at least 1,000 VND")
    @Schema(description = "Amount in Vietnamese Dong (VND)", example = "250000")
    private BigDecimal amount;

    @Schema(description = "Order description shown on VNPay checkout", example = "Thanh toan don hang ORD-20261013-001")
    private String orderInfo;

    @Schema(description = "Bank code shortcut (optional: VNPAYQR, NCB, VISA, etc.). Leave empty for full selection.", example = "NCB")
    private String bankCode;

    @Schema(description = "Display language on VNPay gateway (vn or en)", example = "vn", defaultValue = "vn")
    private String language;
}
