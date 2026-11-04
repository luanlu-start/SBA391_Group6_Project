package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Response returned after generating a signed VNPay payment URL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing the generated VNPay checkout URL")
public class PaymentCreationResponse implements Serializable {

    @Schema(description = "Status response code", example = "00")
    private String code;

    @Schema(description = "Status response message", example = "success")
    private String message;

    @Schema(description = "Signed redirect URL to the VNPay checkout portal", example = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...")
    private String paymentUrl;

    @Schema(description = "Unique VNPay transaction reference generated for this payment", example = "ORD-20261013-001")
    private String txnRef;
}
