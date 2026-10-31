package com.fptpost.modules.order.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.dto.response.MarketOrderAcceptResponse;
import com.fptpost.modules.order.dto.response.MarketplaceOrderDetailResponse;
import com.fptpost.modules.order.dto.response.MarketplaceOrderResponse;
import com.fptpost.modules.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
@Tag(name = "Marketplace", description = "Sàn đơn & Ghép đơn (MKT) - UC-16, UC-17")
public class MarketController {

    private final OrderService orderService;
    private final UserRepository userRepository;

    @GetMapping("/orders")
    @Operation(summary = "API-039: Sàn đơn (SCR-28 / UC-16)", description = "Lấy danh sách các đơn hàng đang mở trên sàn")
    public ResponseEntity<ApiResponse<Page<MarketplaceOrderResponse>>> getMarketplaceOrders(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<MarketplaceOrderResponse> responses = orderService.viewAvailableOrder(pageable, keyword);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sàn đơn thành công", responses));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "API-041: Chi tiết đơn trên sàn (SCR-30)", description = "Xem chi tiết kiện hàng trên sàn theo ID hoặc mã đơn hàng (đã ẩn thông tin nhạy cảm trước khi nhận)")
    public ResponseEntity<ApiResponse<MarketplaceOrderDetailResponse>> getMarketOrderDetail(
            @PathVariable("id") String id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User currentUser = resolveUser(jwt);
        MarketplaceOrderDetailResponse detail = orderService.getMarketOrderDetail(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết đơn trên sàn thành công", detail));
    }

    @PostMapping("/orders/{id}/accept")
    @Operation(summary = "API-043: Nhận đơn theo giá niêm yết (UC-17)", description = "Người giao nhận đơn hàng khả dụng trên sàn theo ID hoặc mã đơn hàng, kiểm tra điều kiện và chuyển sang Chờ ký quỹ")
    public ResponseEntity<ApiResponse<MarketOrderAcceptResponse>> acceptOrder(
            @PathVariable("id") String id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        MarketOrderAcceptResponse response = orderService.acceptMarketOrder(id, courierUser);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    private User resolveUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            return null;
        }
        try {
            UUID publicId = UUID.fromString(jwt.getSubject());
            return userRepository.findByPublicId(publicId).orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }
}
