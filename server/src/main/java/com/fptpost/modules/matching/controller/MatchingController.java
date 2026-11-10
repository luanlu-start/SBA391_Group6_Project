package com.fptpost.modules.matching.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.matching.dto.request.QuickBidRequest;
import com.fptpost.modules.matching.dto.response.OrderCourierMatchResponse;
import com.fptpost.modules.matching.dto.response.TripOrderMatchResponse;
import com.fptpost.modules.matching.service.IMatchingService;
import com.fptpost.modules.order.dto.response.MarketOrderAcceptResponse;
import com.fptpost.modules.order.dto.response.OfferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matching")
@RequiredArgsConstructor
@Tag(name = "Matching", description = "Gợi ý ghép đơn - chuyến tiện đường (MKT / UC-21)")
public class MatchingController {

    private final IMatchingService matchingService;
    private final UserRepository userRepository;

    // ==========================================
    // API-053: Đơn gợi ý cho chuyến đi (COURIER)
    // ==========================================
    @GetMapping("/orders-for-trip/{tripId}")
    @Operation(summary = "API-053: Đơn gợi ý cho chuyến đi", description = "Gợi ý các đơn hàng tiện đường độ lệch <= 3km và phù hợp sức chứa cho chuyến đi")
    public ResponseEntity<ApiResponse<List<TripOrderMatchResponse>>> getOrdersForTrip(
            @PathVariable("tripId") Long tripId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        List<TripOrderMatchResponse> responses = matchingService.getOrdersForTrip(tripId, courierUser);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng tiện đường gợi ý thành công", responses));
    }

    // ==========================================
    // API-054: Người giao gợi ý cho đơn (SENDER)
    // ==========================================
    @GetMapping("/couriers-for-order/{orderId}")
    @Operation(summary = "API-054: Người giao gợi ý cho đơn", description = "Gợi ý các chuyến đi và tài xế tiện đường phù hợp nhất cho đơn hàng đang mở")
    public ResponseEntity<ApiResponse<List<OrderCourierMatchResponse>>> getCouriersForOrder(
            @PathVariable("orderId") Long orderId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User senderUser = resolveUser(jwt);
        if (senderUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        List<OrderCourierMatchResponse> responses = matchingService.getCouriersForOrder(orderId, senderUser);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người giao tiện đường gợi ý thành công", responses));
    }

    // ==========================================
    // Nhận đơn nhanh từ gợi ý chuyến (Flow bước 5 / BR-F-038)
    // ==========================================
    @PostMapping("/trips/{tripId}/orders/{orderId}/quick-accept")
    @Operation(summary = "Nhận đơn nhanh từ danh sách gợi ý chuyến đi", description = "Người giao nhận đơn hàng trực tiếp từ gợi ý và tự động gắn vào chuyến đi")
    public ResponseEntity<ApiResponse<MarketOrderAcceptResponse>> quickAcceptOrder(
            @PathVariable("tripId") Long tripId,
            @PathVariable("orderId") Long orderId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        MarketOrderAcceptResponse response = matchingService.quickAcceptOrder(tripId, orderId, courierUser);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    // ==========================================
    // Chào giá nhanh từ gợi ý chuyến (Flow bước 5 / BR-F-038)
    // ==========================================
    @PostMapping("/trips/{tripId}/orders/{orderId}/quick-bid")
    @Operation(summary = "Chào giá nhanh từ danh sách gợi ý chuyến đi", description = "Người giao gửi báo giá nhanh cho đơn hàng tiện đường từ màn hình chuyến đi")
    public ResponseEntity<ApiResponse<OfferResponse>> quickBidOrder(
            @PathVariable("tripId") Long tripId,
            @PathVariable("orderId") Long orderId,
            @Valid @RequestBody QuickBidRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        OfferResponse response = matchingService.quickBidOrder(tripId, orderId, request, courierUser);
        return ResponseEntity.ok(ApiResponse.success("Gửi chào giá thành công", response));
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
