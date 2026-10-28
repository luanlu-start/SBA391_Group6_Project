package com.fptpost.modules.payment.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.payment.dto.EscrowReleaseResponse;
import com.fptpost.modules.payment.dto.EscrowResponse;
import com.fptpost.modules.payment.service.OrderPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller quản lý ký quỹ và giải ngân tiền cho người giao (UC-36).
 * Cung cấp API-092 (xem thông tin ký quỹ đơn) và API-093 (Admin giải ngân tiền ký quỹ thủ công).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Escrow Management", description = "Quản lý ký quỹ và giải ngân cho người giao hàng (UC-36)")
public class EscrowController {

    private final OrderPaymentService orderPaymentService;
    private final UserRepository userRepository;

    @Operation(summary = "Xem thông tin ký quỹ đơn hàng (UC-36, API-092)",
            description = "Lấy thông tin ký quỹ của đơn hàng. Chỉ Người gửi (sender), Người giao (courier) hoặc Admin mới có quyền xem.")
    @GetMapping("/api/v1/orders/{id}/escrow")
    public ResponseEntity<ApiResponse<EscrowResponse>> getOrderEscrow(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        Long currentUserId = resolveUserId(jwt);
        boolean isAdmin = checkIsAdmin(jwt, authentication);
        log.info("Request get escrow info for orderId={}, currentUserId={}, isAdmin={}", id, currentUserId, isAdmin);

        EscrowResponse response = orderPaymentService.getEscrowByOrderId(id, currentUserId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin ký quỹ đơn hàng thành công", response));
    }

    @Operation(summary = "Admin giải ngân tiền ký quỹ thủ công (UC-36, API-093)",
            description = "Quản trị viên kích hoạt giải ngân khoản tiền ký quỹ cho người giao hàng khi đơn hoàn thành.")
    @PostMapping("/api/v1/admin/escrow/{orderId}/release")
    public ResponseEntity<ApiResponse<EscrowReleaseResponse>> releaseEscrow(
            @PathVariable("orderId") Long orderId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        Long operatorUserId = resolveUserId(jwt);
        boolean isAdmin = checkIsAdmin(jwt, authentication);
        log.info("Admin release escrow request for orderId={}, operatorUserId={}, isAdmin={}", orderId, operatorUserId, isAdmin);

        EscrowReleaseResponse response = orderPaymentService.releaseEscrow(orderId, operatorUserId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Giải ngân tiền ký quỹ cho người giao hàng thành công", response));
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

    private boolean checkIsAdmin(Jwt jwt, Authentication authentication) {
        if (authentication != null && authentication.getAuthorities() != null) {
            boolean hasAdminRole = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
            if (hasAdminRole) {
                return true;
            }
        }
        if (jwt != null) {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null && (roles.contains("ADMIN") || roles.contains("ROLE_ADMIN"))) {
                return true;
            }
        }
        return false;
    }
}
