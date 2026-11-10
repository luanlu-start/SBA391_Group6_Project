package com.fptpost.modules.relay.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.relay.dto.*;
import com.fptpost.modules.relay.service.IRelayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Relay Handover", description = "Quản lý giao tiếp sức và bàn giao giữa các chặng")
public class RelayController {

    private final IRelayService relayService;
    private final UserRepository userRepository;

    // ==========================================
    // Đánh dấu đã đến điểm hẹn (Sẵn sàng bàn giao)
    // ==========================================
    @PatchMapping("/api/v1/orders/{id}/legs/{legNo}/ready")
    @Operation(summary = "Đánh dấu đã đến điểm hẹn bàn giao", description = "Chuyển trạng thái chặng sang Sẵn sàng bàn giao khi tài xế đã tới điểm hẹn")
    @PreAuthorize("hasRole('COURIER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderLegResponse>> markReadyForHandover(
            @PathVariable("id") Long orderId,
            @PathVariable("legNo") Integer legNo,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courier = resolveUser(jwt);
        OrderLegResponse response = relayService.markReadyForHandover(orderId, legNo, courier);
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật trạng thái sẵn sàng bàn giao tại điểm hẹn", response));
    }

    // ==========================================
    // API-083: Bàn giao chặng bằng QR (COURIER)
    // ==========================================
    @PostMapping("/api/v1/orders/{id}/legs/{legNo}/handover")
    @Operation(summary = "Xác nhận bàn giao chặng bằng QR", description = "Người giao chặng tiếp theo quét mã QR/nhập mã xác nhận kèm ảnh để chuyển giao trách nhiệm chặng")
    @PreAuthorize("hasRole('COURIER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<HandoverConfirmResponse>> confirmHandover(
            @PathVariable("id") Long orderId,
            @PathVariable("legNo") Integer legNo,
            @Valid @RequestBody HandoverConfirmRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courier = resolveUser(jwt);
        HandoverConfirmResponse response = relayService.confirmHandover(orderId, legNo, request, courier);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    // ==========================================
    // Sinh / Làm mới mã QR bàn giao (COURIER chặng hiện tại)
    // ==========================================
    @PostMapping("/api/v1/orders/{id}/legs/{legNo}/handover-token")
    @Operation(summary = "Sinh mã QR bàn giao chặng", description = "Người giao chặng hiện tại tạo mã QR (hiệu lực 10 phút) để người giao tiếp theo quét")
    @PreAuthorize("hasRole('COURIER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<HandoverTokenResponse>> generateHandoverToken(
            @PathVariable("id") Long orderId,
            @PathVariable("legNo") Integer legNo,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courier = resolveUser(jwt);
        HandoverTokenResponse response = relayService.generateHandoverToken(orderId, legNo, courier);
        return ResponseEntity.ok(ApiResponse.success("Tạo mã bàn giao thành công (hiệu lực 10 phút)", response));
    }

    // ==========================================
    // Lấy mã QR bàn giao còn hiệu lực
    // ==========================================
    @GetMapping("/api/v1/orders/{id}/legs/{legNo}/handover-token")
    @Operation(summary = "Lấy mã QR bàn giao còn hiệu lực", description = "Lấy mã QR bàn giao hiện tại của chặng nếu chưa hết hạn")
    @PreAuthorize("hasRole('COURIER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<HandoverTokenResponse>> getActiveHandoverToken(
            @PathVariable("id") Long orderId,
            @PathVariable("legNo") Integer legNo,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courier = resolveUser(jwt);
        HandoverTokenResponse response = relayService.getActiveHandoverToken(orderId, legNo, courier);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin mã bàn giao thành công", response));
    }

    // ==========================================
    // Lấy thông tin chi tiết các chặng của đơn tiếp sức
    // ==========================================
    @GetMapping("/api/v1/orders/{id}/legs")
    @Operation(summary = "Lấy chi tiết các chặng tiếp sức", description = "Xem tiến trình các chặng, điểm hẹn bàn giao và lịch sử bàn giao của đơn")
    public ResponseEntity<ApiResponse<RelayOrderDetailResponse>> getRelayOrderDetails(
            @PathVariable("id") Long orderId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User user = jwt != null ? resolveUser(jwt) : null;
        RelayOrderDetailResponse response = relayService.getRelayOrderDetails(orderId, user);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin chặng tiếp sức thành công", response));
    }

    // ==========================================
    // Báo cáo sự cố khi bàn giao tại điểm hẹn
    // ==========================================
    @PostMapping("/api/v1/orders/{id}/legs/{legNo}/incident")
    @Operation(summary = "Báo sự cố bàn giao chặng", description = "Người giao báo cáo sự cố hư hỏng hàng hoặc không gặp được đối tác tại điểm hẹn")
    @PreAuthorize("hasRole('COURIER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<HandoverIncidentResponse>> reportIncident(
            @PathVariable("id") Long orderId,
            @PathVariable("legNo") Integer legNo,
            @Valid @RequestBody HandoverIncidentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courier = resolveUser(jwt);
        HandoverIncidentResponse response = relayService.reportIncident(orderId, legNo, request, courier);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    // ==========================================
    // Danh sách điểm hẹn bàn giao an toàn đã duyệt
    // ==========================================
    @GetMapping("/api/v1/relay/meeting-points")
    @Operation(summary = "Danh sách điểm hẹn bàn giao an toàn", description = "Lấy các điểm công cộng đã duyệt (cổng trường, thư viện...) để làm điểm bàn giao tiếp sức")
    public ResponseEntity<ApiResponse<List<MeetingPointResponse>>> getSafeMeetingPoints(
            @RequestParam(value = "campusArea", required = false) String campusArea
    ) {
        List<MeetingPointResponse> responses = relayService.getSafeMeetingPoints(campusArea);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách điểm hẹn an toàn thành công", responses));
    }

    // ==========================================
    // Danh sách chặng tiếp sức của tài xế đang đăng nhập
    // ==========================================
    @GetMapping("/api/v1/courier/relay-legs")
    @Operation(summary = "Danh sách chặng tiếp sức của tài xế", description = "Các chặng tiếp sức tài xế đang thực hiện hoặc cần bàn giao")
    @PreAuthorize("hasRole('COURIER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderLegResponse>>> getCourierRelayLegs(
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courier = resolveUser(jwt);
        List<OrderLegResponse> responses = relayService.getCourierRelayLegs(courier);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách chặng tiếp sức thành công", responses));
    }

    // ==========================================
    // Ước tính cước phí giao tiếp sức (UC-32 / BR-R-047)
    // ==========================================
    @PostMapping("/api/v1/orders/relay/estimate")
    @Operation(summary = "Ước tính cước phí giao tiếp sức", description = "Tính toán khoảng cách các chặng và phân chia cước phí theo tỷ lệ quãng đường (BR-R-047)")
    public ResponseEntity<ApiResponse<RelayPricingEstimateResponse>> estimateRelayPricing(
            @RequestBody RelayPricingEstimateRequest request
    ) {
        RelayPricingEstimateResponse response = relayService.estimateRelayPricing(request);
        return ResponseEntity.ok(ApiResponse.success("Ước tính cước phí tiếp sức thành công", response));
    }

    // ==========================================
    // Tạo đơn hàng giao tiếp sức
    // ==========================================
    @PostMapping("/api/v1/orders/relay")
    @Operation(summary = "Tạo đơn hàng giao tiếp sức", description = "Tạo đơn hàng chia thành nhiều chặng tiếp sức kèm điểm hẹn bàn giao an toàn")
    public ResponseEntity<ApiResponse<RelayOrderDetailResponse>> createRelayOrder(
            @Valid @RequestBody RelayOrderCreateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User sender = resolveUser(jwt);
        RelayOrderDetailResponse response = relayService.createRelayOrder(request, sender);
        return ResponseEntity.ok(ApiResponse.success("Tạo đơn hàng tiếp sức thành công", response));
    }

    private User resolveUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        try {
            UUID publicId = UUID.fromString(jwt.getSubject());
            return userRepository.findByPublicId(publicId)
                    .orElseThrow(() -> new AppException(GlobalErrorCode.UNAUTHORIZED));
        } catch (Exception e) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
    }
}
