package com.fptpost.modules.payment.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.payment.dto.PaymentCreationRequest;
import com.fptpost.modules.payment.dto.PaymentCreationResponse;
import com.fptpost.modules.payment.dto.VNPayIpnResponse;
import com.fptpost.modules.payment.dto.VNPayReturnResponse;
import com.fptpost.modules.payment.service.VNPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller exposing VNPay payment gateway endpoints:
 * - URL creation
 * - Synchronous browser return callback
 * - Asynchronous server-to-server IPN webhook
 */
@Slf4j
@RestController
@RequestMapping({"/api/payment", "/api/v1/payment"})
@RequiredArgsConstructor
@Tag(name = "Payment Gateway", description = "Production VNPay Payment Gateway integration APIs")
public class PaymentController {

    private final VNPayService vnPayService;

    @Operation(summary = "Create VNPay Payment URL", description = "Generates a signed redirect URL to VNPay payment portal.")
    @PostMapping("/create-vnpay-url")
    public ResponseEntity<ApiResponse<PaymentCreationResponse>> createPaymentUrl(
            @Valid @RequestBody PaymentCreationRequest request,
            HttpServletRequest httpRequest) {
        log.info("Received request to create VNPay payment URL for orderCode: {}", request.getOrderCode());
        PaymentCreationResponse response = vnPayService.createPaymentUrl(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("VNPay redirect URL created successfully", response));
    }

    @Operation(summary = "VNPay Browser Return Callback", description = "Synchronous return URL handler called when customer redirects back from VNPay.")
    @GetMapping("/vnpay-return")
    public ResponseEntity<ApiResponse<VNPayReturnResponse>> handleVNPayReturn(
            @RequestParam Map<String, String> queryParams) {
        log.info("Received VNPay return callback with {} parameters", queryParams.size());
        VNPayReturnResponse response = vnPayService.processReturn(queryParams);
        return ResponseEntity.ok(ApiResponse.success("VNPay transaction return processed", response));
    }

    @Operation(summary = "VNPay Asynchronous IPN Webhook", description = "Server-to-server IPN listener called by VNPay to notify payment completion.")
    @GetMapping(value = "/vnpay-ipn", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VNPayIpnResponse> handleVNPayIpn(
            @RequestParam Map<String, String> queryParams) {
        log.info("Received VNPay IPN notification with {} parameters", queryParams.size());
        VNPayIpnResponse response = vnPayService.processIpn(queryParams);
        return ResponseEntity.ok(response);
    }
}
