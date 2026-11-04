package com.fptpost.modules.payment.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.payment.dto.WithdrawalCreationRequest;
import com.fptpost.modules.payment.dto.WithdrawalResponse;
import com.fptpost.modules.payment.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller quản lý yêu cầu rút tiền từ ví về ngân hàng (UC-40, API-097, API-098, API-099).
 */
@Slf4j
@RestController
@RequestMapping({"/api/v1/wallet/withdrawals", "/api/wallet/withdrawals"})
@RequiredArgsConstructor
@Tag(name = "Withdrawal", description = "Quản lý yêu cầu rút tiền ví FPTPost về tài khoản ngân hàng")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;
    private final UserRepository userRepository;

    @Operation(summary = "Tạo yêu cầu rút tiền về ngân hàng (UC-40, API-097)",
            description = "Tạo yêu cầu rút tiền từ ví về ngân hàng. Số dư khả dụng sẽ được chuyển sang trạng thái tạm giữ chờ duyệt.")
    @PostMapping
    public ResponseEntity<ApiResponse<WithdrawalResponse>> createWithdrawal(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WithdrawalCreationRequest request) {
        Long userId = resolveUserId(jwt);
        log.info("User {} creating withdrawal request for {} VND to bank {}", userId, request.getAmount(), request.getBankName());
        WithdrawalResponse response = withdrawalService.createWithdrawalRequest(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Tạo yêu cầu rút tiền thành công", response));
    }

    @Operation(summary = "Xem danh sách yêu cầu rút tiền của tôi (UC-40, API-098)",
            description = "Lấy danh sách phân trang các yêu cầu rút tiền của tài khoản hiện tại.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<WithdrawalResponse>>> getMyWithdrawals(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Long userId = resolveUserId(jwt);
        PageResponse<WithdrawalResponse> response = withdrawalService.getMyWithdrawals(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách yêu cầu rút tiền thành công", response));
    }

    @Operation(summary = "Xem chi tiết một yêu cầu rút tiền (UC-40)",
            description = "Lấy chi tiết yêu cầu rút tiền theo ID.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> getWithdrawalById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        Long userId = resolveUserId(jwt);
        WithdrawalResponse response = withdrawalService.getWithdrawalById(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết yêu cầu rút tiền thành công", response));
    }

    @Operation(summary = "Hủy yêu cầu rút tiền (UC-40, API-099)",
            description = "Hủy yêu cầu rút tiền khi còn ở trạng thái chờ duyệt (PENDING_REVIEW), hoàn trả tiền về số dư khả dụng.")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> cancelWithdrawal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        Long userId = resolveUserId(jwt);
        log.info("User {} cancelling withdrawal request {}", userId, id);
        WithdrawalResponse response = withdrawalService.cancelWithdrawalRequest(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Hủy yêu cầu rút tiền thành công", response));
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
