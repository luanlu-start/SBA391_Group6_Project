package com.fptpost.modules.payment.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.payment.config.PaymentDemoSeeder;
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
import java.util.Optional;

/**
 * REST Controller exposing VNPay payment gateway endpoints:
 * - URL creation
 * - Synchronous browser return callback
 * - Asynchronous server-to-server IPN webhook
 * - Seed testing data endpoint
 */
@Slf4j
@RestController
@RequestMapping({"/api/payment", "/api/v1/payment"})
@RequiredArgsConstructor
@Tag(name = "Payment Gateway", description = "Production VNPay Payment Gateway integration APIs")
public class PaymentController {

    private final VNPayService vnPayService;
    private final Optional<PaymentDemoSeeder> paymentDemoSeeder;

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

    @Operation(summary = "Khởi tạo data mẫu test VNPay", description = "Tạo các đơn hàng mẫu (ORD-TEST-001, 002, 003, 004) phục vụ kiểm thử thủ công và tự động.")
    @PostMapping("/test-data/seed")
    public ResponseEntity<ApiResponse<Map<String, String>>> seedTestData() {
        if (paymentDemoSeeder.isPresent()) {
            paymentDemoSeeder.get().seedDemoData();
            return ResponseEntity.ok(ApiResponse.success("Khởi tạo dữ liệu mẫu kiểm thử VNPay thành công",
                    Map.of(
                            "ORD-TEST-001", "150,000 VND (PENDING - Sẵn sàng test duyệt IPN/Return)",
                            "ORD-TEST-002", "250,000 VND (PENDING - Sẵn sàng test tạo URL thanh toán mới)",
                            "ORD-TEST-003", "500,000 VND (SUCCESS - Dùng để test chống trùng lặp IPN mã 02)",
                            "ORD-TEST-004", "1,000,000 VND (CANCELLED - Đơn đã hủy thanh toán mã 24)"
                    )));
        }
        return ResponseEntity.ok(ApiResponse.success("PaymentDemoSeeder không khả dụng trong profile hiện tại", Map.of()));
    }
}
