package com.fptpost.modules.order.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.dto.request.HandoverConfirmRequest;
import com.fptpost.modules.order.dto.response.HandoverQrResponse;
import com.fptpost.modules.order.dto.response.OrderLegResponse;
import com.fptpost.modules.order.dto.response.RelayHandoverResultResponse;
import com.fptpost.modules.order.dto.response.RelayMeetingPointDto;
import com.fptpost.modules.order.dto.response.RelayOrderResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.OrderLeg;
import com.fptpost.modules.order.repository.OrderLegRepository;
import com.fptpost.modules.order.service.RelayHandoverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Controller xử lý nghiệp vụ Bàn giao giữa các chặng tiếp sức (phân hệ REL, BR-F-060, BR-F-061, BR-R-048, API-083).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Relay Handover", description = "Endpoints bàn giao giữa các chặng trong đơn giao tiếp sức")
public class RelayHandoverController {

    private final RelayHandoverService relayHandoverService;
    private final OrderLegRepository orderLegRepository;
    private final UserRepository userRepository;

    @GetMapping("/relay")
    @PreAuthorize("hasRole('COURIER') or hasRole('SENDER') or hasRole('ADMIN')")
    @Operation(summary = "Lấy danh sách các đơn tiếp sức và trạng thái các chặng",
               description = "Hỗ trợ lọc theo 'available' (chặng đang tìm tài xế), 'my' (chặng tôi phụ trách), hoặc 'all'")
    public ResponseEntity<ApiResponse<List<RelayOrderResponse>>> getRelayOrders(
            @RequestParam(value = "filter", defaultValue = "all") String filter,
            @AuthenticationPrincipal Jwt jwt) {
        Long courierId = null;
        if (jwt != null && jwt.getSubject() != null) {
            try {
                courierId = resolveUserId(jwt);
            } catch (Exception ignored) {}
        }
        List<RelayOrderResponse> response = relayHandoverService.getRelayOrders(filter, courierId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn tiếp sức thành công", response));
    }

    @GetMapping("/relay/meeting-points")
    @Operation(summary = "Danh sách điểm hẹn bàn giao an toàn đã kiểm duyệt (BR-F-061)",
               description = "Đề xuất các điểm hẹn công cộng an toàn quanh campus FPT và các nút giao thông có bảo vệ, camera an ninh")
    public ResponseEntity<ApiResponse<List<RelayMeetingPointDto>>> getSafeMeetingPoints() {
        List<RelayMeetingPointDto> points = relayHandoverService.getSafeMeetingPoints();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách điểm hẹn an toàn thành công", points));
    }

    @GetMapping("/{id}/legs/{legNo}")
    @Operation(summary = "Xem chi tiết một chặng của đơn tiếp sức",
               description = "Lấy thông tin quãng đường, điểm xuất phát, điểm hẹn bàn giao và tài xế phụ trách")
    public ResponseEntity<ApiResponse<OrderLegResponse>> getLegDetail(
            @PathVariable Long id,
            @PathVariable Integer legNo) {
        List<OrderLeg> legs = orderLegRepository.findByOrderIdOrderByLegNoAsc(id);
        OrderLeg target = legs.stream()
                .filter(l -> Objects.equals(l.getLegNo(), legNo))
                .findFirst()
                .orElseThrow(() -> new AppException(OrderErrorCode.LEG_NOT_FOUND));

        OrderLegResponse response = relayHandoverService.enrichLegResponse(target);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/legs-detailed")
    @Operation(summary = "Xem danh sách chi tiết các chặng của đơn tiếp sức kèm thông tin tài xế và bằng chứng bàn giao",
               description = "Cung cấp đầy đủ trạng thái các chặng và lịch sử bàn giao")
    public ResponseEntity<ApiResponse<List<OrderLegResponse>>> getDetailedOrderLegs(@PathVariable Long id) {
        List<OrderLeg> legs = orderLegRepository.findByOrderIdOrderByLegNoAsc(id);
        List<OrderLegResponse> responses = relayHandoverService.enrichLegResponses(legs);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PostMapping("/{id}/legs/{legNo}/handover-token")
    @PreAuthorize("hasRole('COURIER') or hasRole('ADMIN')")
    @Operation(summary = "Sinh mã QR và OTP bàn giao cho chặng tiếp sức (PRM-097)",
               description = "Tài xế chặng hiện tại tạo mã QR có chữ ký HMAC, hiệu lực 10 phút, để tài xế kế tiếp quét")
    public ResponseEntity<ApiResponse<HandoverQrResponse>> generateHandoverQr(
            @PathVariable Long id,
            @PathVariable Integer legNo,
            @AuthenticationPrincipal Jwt jwt) {
        Long courierId = resolveUserId(jwt);
        HandoverQrResponse response = relayHandoverService.getHandoverQr(id, legNo, courierId);
        return ResponseEntity.ok(ApiResponse.success("Tạo mã QR bàn giao thành công", response));
    }

    @GetMapping("/{id}/legs/{legNo}/handover-token")
    @PreAuthorize("hasRole('COURIER') or hasRole('ADMIN')")
    @Operation(summary = "Lấy mã QR bàn giao hiện tại cho chặng tiếp sức (PRM-097)")
    public ResponseEntity<ApiResponse<HandoverQrResponse>> getHandoverQr(
            @PathVariable Long id,
            @PathVariable Integer legNo,
            @AuthenticationPrincipal Jwt jwt) {
        Long courierId = resolveUserId(jwt);
        HandoverQrResponse response = relayHandoverService.getHandoverQr(id, legNo, courierId);
        return ResponseEntity.ok(ApiResponse.success("Lấy mã QR bàn giao thành công", response));
    }

    @PostMapping(value = "/{id}/legs/{legNo}/handover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('COURIER')")
    @Operation(summary = "Xác nhận bàn giao giữa các chặng bằng QR và ảnh hàng (API-083, BR-F-060, BR-R-048)",
               description = "Tài xế chặng tiếp theo quét QR, tải ảnh tình trạng kiện hàng để chuyển giao trách nhiệm chặng")
    public ResponseEntity<ApiResponse<RelayHandoverResultResponse>> confirmHandover(
            @PathVariable Long id,
            @PathVariable Integer legNo,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("qrToken") String qrToken,
            @RequestParam("photo") MultipartFile photo,
            @RequestParam(value = "meetingPoint", required = false) String meetingPoint,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam(value = "latitude", required = false) BigDecimal latitude,
            @RequestParam(value = "longitude", required = false) BigDecimal longitude) {

        Long receivingCourierId = resolveUserId(jwt);

        HandoverConfirmRequest request = HandoverConfirmRequest.builder()
                .qrToken(qrToken)
                .meetingPoint(meetingPoint)
                .note(note)
                .latitude(latitude)
                .longitude(longitude)
                .build();

        RelayHandoverResultResponse result = relayHandoverService.confirmHandover(
                id, legNo, receivingCourierId, request, photo
        );

        return ResponseEntity.ok(ApiResponse.success("Xác nhận bàn giao kiện hàng thành công", result));
    }

    @PostMapping("/{id}/legs/{legNo}/accept")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(summary = "Tài xế nhận phụ trách một chặng tiếp sức",
               description = "Tiếp nhận vận chuyển một chặng tiếp sức đang mở tìm tài xế")
    public ResponseEntity<ApiResponse<OrderLegResponse>> acceptRelayLeg(
            @PathVariable Long id,
            @PathVariable Integer legNo,
            @AuthenticationPrincipal Jwt jwt) {
        Long courierId = resolveUserId(jwt);
        OrderLegResponse leg = relayHandoverService.acceptLeg(id, legNo, courierId);
        return ResponseEntity.ok(ApiResponse.success("Tiếp nhận chặng vận chuyển thành công", leg));
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
