package com.fptpost.modules.payment.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.payment.dto.PaymentCreationResponse;
import com.fptpost.modules.payment.dto.WalletResponse;
import com.fptpost.modules.payment.dto.WalletTopupRequest;
import com.fptpost.modules.payment.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller quản lý Ví điện tử và Nạp tiền qua VNPay (UC-34 & UC-38).
 */
@Slf4j
@RestController
@RequestMapping({"/api/v1/wallet", "/api/wallet"})
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Quản lý ví điện tử và nạp tiền FPTPost")
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    @Operation(summary = "Xem thông tin và số dư ví (UC-38)", description = "Lấy số dư khả dụng, tiền ký quỹ và tiền chờ rút của tài khoản hiện tại.")
    @GetMapping
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet(@AuthenticationPrincipal Jwt jwt) {
        Long userId = resolveUserId(jwt);
        WalletResponse response = walletService.getWallet(userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin ví thành công", response));
    }

    @Operation(summary = "Nạp tiền vào ví qua VNPay (UC-34, API-084)", description = "Khởi tạo giao dịch và sinh URL thanh toán VNPay có chữ ký số để nạp tiền vào ví.")
    @PostMapping("/topups")
    public ResponseEntity<ApiResponse<PaymentCreationResponse>> topupWallet(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WalletTopupRequest request,
            HttpServletRequest httpRequest) {
        Long userId = resolveUserId(jwt);
        log.info("User {} requesting wallet topup of {} VND", userId, request.getAmount());
        PaymentCreationResponse response = walletService.createTopupUrl(userId, request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Tạo URL thanh toán VNPay thành công", response));
    }

    private Long resolveUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        UUID publicId = UUID.fromString(jwt.getSubject());
        return userRepository.findByPublicId(publicId)
                .map(User::getId)
                .orElseThrow(() -> new AppException(GlobalErrorCode.UNAUTHORIZED));
    }
}
