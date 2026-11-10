package com.fptpost.modules.payment.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.payment.dto.BankAccountCreationRequest;
import com.fptpost.modules.payment.dto.BankAccountResponse;
import com.fptpost.modules.payment.service.BankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller quản lý tài khoản ngân hàng nhận tiền của người dùng (UC-39, API-094, API-095, API-096).
 */
@Slf4j
@RestController
@RequestMapping({"/api/v1/me/bank-accounts", "/api/me/bank-accounts"})
@RequiredArgsConstructor
@Tag(name = "Bank Accounts", description = "Quản lý tài khoản ngân hàng nhận tiền (UC-39)")
public class BankAccountController {

    private final BankAccountService bankAccountService;
    private final UserRepository userRepository;

    @Operation(summary = "Danh sách tài khoản ngân hàng của tôi (UC-39, API-094)",
            description = "Lấy danh sách các tài khoản ngân hàng đã liên kết, che số tài khoản và hiển thị trạng thái thời gian chờ 24 giờ.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BankAccountResponse>>> getMyBankAccounts(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = resolveUserId(jwt);
        List<BankAccountResponse> accounts = bankAccountService.getMyBankAccounts(userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tài khoản ngân hàng thành công", accounts));
    }

    @Operation(summary = "Thêm tài khoản ngân hàng nhận tiền (UC-39, API-095)",
            description = "Đối chiếu tên chủ tài khoản với họ tên KYC, mã hóa số tài khoản và thiết lập thời gian chờ 24 giờ trước khi rút tiền.")
    @PostMapping
    public ResponseEntity<ApiResponse<BankAccountResponse>> createBankAccount(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BankAccountCreationRequest request) {
        Long userId = resolveUserId(jwt);
        log.info("User {} linking new bank account for bankCode={}", userId, request.getBankCode());
        BankAccountResponse response = bankAccountService.createBankAccount(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Liên kết tài khoản ngân hàng thành công", response));
    }

    @Operation(summary = "Xóa tài khoản ngân hàng của tôi (UC-39, API-096)",
            description = "Xóa tài khoản ngân hàng nhận tiền đã liên kết của người dùng hiện tại.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBankAccount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id) {
        Long userId = resolveUserId(jwt);
        log.info("User {} deleting bank account id={}", userId, id);
        bankAccountService.deleteBankAccount(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Xóa tài khoản ngân hàng thành công", null));
    }

    private Long resolveUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        try {
            UUID publicId = UUID.fromString(jwt.getSubject());
            return userRepository.findByPublicId(publicId)
                    .map(User::getId)
                    .orElseThrow(() -> new AppException(GlobalErrorCode.UNAUTHORIZED));
        } catch (IllegalArgumentException e) {
            try {
                return Long.parseLong(jwt.getSubject());
            } catch (NumberFormatException nfe) {
                throw new AppException(GlobalErrorCode.UNAUTHORIZED);
            }
        }
    }
}
