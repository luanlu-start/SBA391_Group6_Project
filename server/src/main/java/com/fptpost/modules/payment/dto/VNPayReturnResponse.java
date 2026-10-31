package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Result DTO parsed from the synchronous browser return redirect (vnp_ReturnUrl).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Processed result from VNPay return callback")
public class VNPayReturnResponse implements Serializable {

    @Schema(description = "Merchant internal order code / transaction reference", example = "ORD-20261013-001")
    private String orderCode;

    @Schema(description = "Payment amount in Vietnamese Dong (VND)", example = "250000")
    private Long amount;

    @Schema(description = "Bank code used for payment", example = "NCB")
    private String bankCode;

    @Schema(description = "Bank transaction number", example = "VNP14123456")
    private String bankTranNo;

    @Schema(description = "Card or payment method type", example = "ATM")
    private String cardType;

    @Schema(description = "Order description", example = "Thanh toan don hang ORD-20261013-001")
    private String orderInfo;

    @Schema(description = "Payment execution timestamp (yyyyMMddHHmmss)", example = "20261013180000")
    private String payDate;

    @Schema(description = "VNPay gateway response code", example = "00")
    private String responseCode;

    @Schema(description = "VNPay gateway transaction reference number", example = "14567890")
    private String transactionNo;

    @Schema(description = "VNPay transaction status code", example = "00")
    private String transactionStatus;

    @Schema(description = "Received secure hash checksum", example = "a1b2c3d4...")
    private String secureHash;

    @Schema(description = "Indicates whether the HMAC-SHA512 signature is authentic", example = "true")
    private boolean validSignature;

    @Schema(description = "Indicates whether the payment was completed successfully", example = "true")
    private boolean success;

    @Schema(description = "Human-readable status or error description", example = "Giao dịch thành công")
    private String message;
}
