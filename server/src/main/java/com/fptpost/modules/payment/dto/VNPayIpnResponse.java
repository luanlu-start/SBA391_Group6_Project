package com.fptpost.modules.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Standard server-to-server IPN response conforming strictly to VNPay's protocol.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Server-to-server IPN acknowledgment response strictly required by VNPay")
public class VNPayIpnResponse implements Serializable {

    @JsonProperty("RspCode")
    @Schema(description = "VNPay standard response code", example = "00")
    private String rspCode;

    @JsonProperty("Message")
    @Schema(description = "VNPay standard response message", example = "Confirm Success")
    private String message;

    public static VNPayIpnResponse success() {
        return new VNPayIpnResponse("00", "Confirm Success");
    }

    public static VNPayIpnResponse orderNotFound() {
        return new VNPayIpnResponse("01", "Order not Found");
    }

    public static VNPayIpnResponse orderAlreadyConfirmed() {
        return new VNPayIpnResponse("02", "Order already confirmed");
    }

    public static VNPayIpnResponse invalidAmount() {
        return new VNPayIpnResponse("04", "Invalid Amount");
    }

    public static VNPayIpnResponse invalidChecksum() {
        return new VNPayIpnResponse("97", "Invalid Checksum");
    }

    public static VNPayIpnResponse unknownError(String msg) {
        return new VNPayIpnResponse("99", msg != null ? msg : "Unknown Error");
    }
}
