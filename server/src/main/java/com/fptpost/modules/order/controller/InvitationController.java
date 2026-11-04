package com.fptpost.modules.order.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.dto.request.InvitationCreateRequest;
import com.fptpost.modules.order.dto.response.CourierSummaryResponse;
import com.fptpost.modules.order.dto.response.FavoriteCourierResponse;
import com.fptpost.modules.order.dto.response.InvitationResponse;
import com.fptpost.modules.order.service.IInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Invitations", description = "Mời trực tiếp người giao & Bưu tá yêu thích (MKT) - UC-20, API-051, API-052")
public class InvitationController {

    private final IInvitationService invitationService;
    private final UserRepository userRepository;

    // ==========================================
    // API-051: Mời trực tiếp người giao
    // ==========================================
    @PostMapping("/orders/{id}/invitations")
    @Operation(summary = "API-051: Mời trực tiếp người giao (UC-20)", description = "Người gửi gửi lời mời nhận đơn hàng trực tiếp tới một bưu tá cụ thể hoặc từ danh sách yêu thích")
    public ResponseEntity<ApiResponse<InvitationResponse>> inviteCourier(
            @PathVariable("id") String orderIdentifier,
            @Valid @RequestBody InvitationCreateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User senderUser = resolveUser(jwt);
        if (senderUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        InvitationResponse response = invitationService.inviteCourier(orderIdentifier, request, senderUser);
        return ResponseEntity.ok(ApiResponse.success("Gửi lời mời trực tiếp thành công", response));
    }

    // ==========================================
    // Lấy danh sách lời mời của đơn hàng (Sender)
    // ==========================================
    @GetMapping("/orders/{id}/invitations")
    @Operation(summary = "Lấy danh sách lời mời của đơn hàng (UC-20)", description = "Xem tất cả các lời mời đã gửi của đơn hàng kèm trạng thái phản hồi")
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> getOrderInvitations(
            @PathVariable("id") String orderIdentifier,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User senderUser = resolveUser(jwt);
        List<InvitationResponse> responses = invitationService.getInvitationsForOrder(orderIdentifier, senderUser);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lời mời thành công", responses));
    }

    // ==========================================
    // Hủy lời mời (Sender)
    // ==========================================
    @DeleteMapping("/orders/{orderId}/invitations/{invitationId}")
    @Operation(summary = "Hủy lời mời giao hàng (UC-20)", description = "Người gửi hủy lời mời đang chờ phản hồi")
    public ResponseEntity<ApiResponse<InvitationResponse>> cancelInvitation(
            @PathVariable("orderId") String orderId,
            @PathVariable("invitationId") Long invitationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User senderUser = resolveUser(jwt);
        if (senderUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        InvitationResponse response = invitationService.cancelInvitation(invitationId, senderUser);
        return ResponseEntity.ok(ApiResponse.success("Hủy lời mời thành công", response));
    }

    // ==========================================
    // Lấy danh sách lời mời nhận được (Courier)
    // ==========================================
    @GetMapping("/me/invitations")
    @Operation(summary = "Danh sách lời mời nhận được (UC-20)", description = "Bưu tá xem các lời mời nhận đơn hàng gửi trực tiếp cho mình")
    public ResponseEntity<ApiResponse<Page<InvitationResponse>>> getMyInvitations(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        Page<InvitationResponse> page = invitationService.getCourierInvitations(courierUser, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lời mời thành công", page));
    }

    // ==========================================
    // Chấp nhận lời mời (Courier)
    // ==========================================
    @PostMapping("/invitations/{id}/accept")
    @Operation(summary = "Chấp nhận lời mời giao hàng (UC-20)", description = "Bưu tá chấp nhận lời mời trực tiếp, đơn hàng được khóa và chuyển sang Chờ ký quỹ")
    public ResponseEntity<ApiResponse<InvitationResponse>> acceptInvitation(
            @PathVariable("id") Long invitationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        InvitationResponse response = invitationService.acceptInvitation(invitationId, courierUser);
        return ResponseEntity.ok(ApiResponse.success("Chấp nhận lời mời giao hàng thành công! Đơn hàng đang chờ người gửi ký quỹ.", response));
    }

    // ==========================================
    // Từ chối lời mời (Courier)
    // ==========================================
    @PostMapping("/invitations/{id}/reject")
    @Operation(summary = "Từ chối lời mời giao hàng (UC-20)", description = "Bưu tá từ chối lời mời trực tiếp")
    public ResponseEntity<ApiResponse<InvitationResponse>> rejectInvitation(
            @PathVariable("id") Long invitationId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        String reason = body != null ? body.get("reason") : null;
        InvitationResponse response = invitationService.rejectInvitation(invitationId, courierUser, reason);
        return ResponseEntity.ok(ApiResponse.success("Từ chối lời mời thành công", response));
    }

    // ==========================================
    // API-052: Thêm/bỏ người giao yêu thích
    // ==========================================
    @PutMapping("/me/favorites/{courierId}")
    @Operation(summary = "API-052: Thêm/bỏ người giao yêu thích (UC-20)", description = "Người gửi lưu hoặc hủy lưu người giao vào danh bạ yêu thích")
    public ResponseEntity<ApiResponse<FavoriteCourierResponse>> toggleFavorite(
            @PathVariable("courierId") Long courierId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User senderUser = resolveUser(jwt);
        if (senderUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        String note = body != null ? body.get("note") : null;
        FavoriteCourierResponse response = invitationService.toggleFavorite(courierId, note, senderUser);
        String message = response != null ? "Đã thêm bưu tá vào danh sách yêu thích" : "Đã xóa bưu tá khỏi danh sách yêu thích";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    // ==========================================
    // Danh sách người giao yêu thích của tôi
    // ==========================================
    @GetMapping("/me/favorites")
    @Operation(summary = "Danh sách người giao yêu thích (UC-20)", description = "Lấy toàn bộ danh sách bưu tá yêu thích đã lưu của người gửi")
    public ResponseEntity<ApiResponse<List<FavoriteCourierResponse>>> getMyFavorites(
            @AuthenticationPrincipal Jwt jwt
    ) {
        User senderUser = resolveUser(jwt);
        if (senderUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        List<FavoriteCourierResponse> responses = invitationService.getMyFavorites(senderUser);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách bưu tá yêu thích thành công", responses));
    }

    // ==========================================
    // Xóa bưu tá khỏi danh sách yêu thích
    // ==========================================
    @DeleteMapping("/me/favorites/{courierId}")
    @Operation(summary = "Xóa bưu tá khỏi danh sách yêu thích (UC-20)", description = "Xóa bưu tá khỏi danh sách yêu thích của người gửi")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            @PathVariable("courierId") Long courierId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User senderUser = resolveUser(jwt);
        if (senderUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        invitationService.removeFavorite(courierId, senderUser);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa bưu tá khỏi danh sách yêu thích", null));
    }

    // ==========================================
    // Tìm kiếm tài xế / bưu tá để mời
    // ==========================================
    @GetMapping("/couriers/search")
    @Operation(summary = "Tìm kiếm người giao hàng (UC-20)", description = "Tìm kiếm tài xế/người giao theo tên, số điện thoại, khu vực hoạt động để gửi lời mời")
    public ResponseEntity<ApiResponse<Page<CourierSummaryResponse>>> searchCouriers(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User currentUser = resolveUser(jwt);
        Page<CourierSummaryResponse> responses = invitationService.searchCouriers(keyword, currentUser, pageable);
        return ResponseEntity.ok(ApiResponse.success("Tìm kiếm bưu tá thành công", responses));
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
