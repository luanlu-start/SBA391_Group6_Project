package com.fptpost.modules.order.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.dto.request.OfferCounterRequest;
import com.fptpost.modules.order.dto.request.OfferCreateRequest;
import com.fptpost.modules.order.dto.response.OfferAcceptResponse;
import com.fptpost.modules.order.dto.response.OfferResponse;
import com.fptpost.modules.order.service.IOfferService;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Offers", description = "Thương lượng & Chào giá (MKT) - UC-18, UC-19")
public class OfferController {

    private final IOfferService offerService;
    private final UserRepository userRepository;

    // ==========================================
    // UC-18: Gửi chào giá (API-044)
    // ==========================================
    @PostMapping("/orders/{id}/offers")
    @Operation(summary = "API-044: Gửi chào giá (UC-18)", description = "Bưu tá gửi mức giá chào và thời gian lấy dự kiến cho đơn hàng đang mở")
    public ResponseEntity<ApiResponse<OfferResponse>> createOffer(
            @PathVariable("id") String orderIdentifier,
            @Valid @RequestBody OfferCreateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        OfferResponse response = offerService.createOffer(orderIdentifier, request, courierUser);
        return ResponseEntity.ok(ApiResponse.success("Gửi chào giá thành công", response));
    }

    // ==========================================
    // UC-18: Rút chào giá (API-045)
    // ==========================================
    @DeleteMapping("/offers/{id}")
    @Operation(summary = "API-045: Rút chào giá (UC-18)", description = "Bưu tá rút lại chào giá đang chờ phản hồi hoặc đang thương lượng")
    public ResponseEntity<ApiResponse<OfferResponse>> withdrawOffer(
            @PathVariable("id") Long offerId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        OfferResponse response = offerService.withdrawOffer(offerId, courierUser);
        return ResponseEntity.ok(ApiResponse.success("Rút chào giá thành công", response));
    }

    // ==========================================
    // UC-18: Chào giá của tôi (API-046)
    // ==========================================
    @GetMapping("/me/offers")
    @Operation(summary = "API-046: Chào giá của tôi (UC-18)", description = "Bưu tá xem danh sách các chào giá mình đã gửi")
    public ResponseEntity<ApiResponse<List<OfferResponse>>> getMyOffers(
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        List<OfferResponse> responses = offerService.getMyOffers(courierUser);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách chào giá thành công", responses));
    }

    // ==========================================
    // UC-19: Danh sách chào giá của một đơn (API-047)
    // ==========================================
    @GetMapping("/orders/{id}/offers")
    @Operation(summary = "API-047: Danh sách chào giá của đơn (UC-19 / SCR-21)", description = "Người gửi xem danh sách các chào giá của đơn hàng; Bưu tá xem chào giá của chính mình")
    public ResponseEntity<ApiResponse<List<OfferResponse>>> getOffersForOrder(
            @PathVariable("id") String orderIdentifier,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User currentUser = resolveUser(jwt);
        if (currentUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        List<OfferResponse> responses = offerService.getOffersForOrder(orderIdentifier, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách chào giá của đơn thành công", responses));
    }

    // ==========================================
    // UC-19: Chấp nhận chào giá (API-048)
    // ==========================================
    @PostMapping("/offers/{id}/accept")
    @Operation(summary = "API-048: Chấp nhận chào giá (UC-19)", description = "Người gửi chấp nhận chào giá của bưu tá, chốt người giao và chuyển đơn sang Chờ ký quỹ")
    public ResponseEntity<ApiResponse<OfferAcceptResponse>> acceptOffer(
            @PathVariable("id") Long offerId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User senderUser = resolveUser(jwt);
        if (senderUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        OfferAcceptResponse response = offerService.acceptOffer(offerId, senderUser);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    // ==========================================
    // UC-19: Từ chối chào giá (API-049)
    // ==========================================
    @PostMapping("/offers/{id}/reject")
    @Operation(summary = "API-049: Từ chối chào giá (UC-19)", description = "Người gửi từ chối một lời chào giá của bưu tá")
    public ResponseEntity<ApiResponse<OfferResponse>> rejectOffer(
            @PathVariable("id") Long offerId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User senderUser = resolveUser(jwt);
        if (senderUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        OfferResponse response = offerService.rejectOffer(offerId, senderUser);
        return ResponseEntity.ok(ApiResponse.success("Đã từ chối chào giá", response));
    }

    // ==========================================
    // UC-19: Phản giá (API-050)
    // ==========================================
    @PostMapping("/offers/{id}/counter")
    @Operation(summary = "API-050: Phản giá (UC-19)", description = "Người gửi hoặc Bưu tá phản đề xuất mức giá mới (tối đa 3 vòng thương lượng)")
    public ResponseEntity<ApiResponse<OfferResponse>> counterOffer(
            @PathVariable("id") Long offerId,
            @Valid @RequestBody OfferCounterRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User currentUser = resolveUser(jwt);
        if (currentUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        OfferResponse response = offerService.counterOffer(offerId, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Đã gửi mức giá phản đề xuất", response));
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
