package com.fptpost.modules.trip.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.trip.dto.request.TripCreateRequest;
import com.fptpost.modules.trip.dto.request.TripUpdateRequest;
import com.fptpost.modules.trip.dto.response.TripAttachedOrderResponse;
import com.fptpost.modules.trip.dto.response.TripResponse;
import com.fptpost.modules.trip.model.TripStatus;
import com.fptpost.modules.trip.service.ITripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Trips", description = "Quản lý Chuyến đi & Ghép đơn tiện đường (TRP)")
public class TripController {

    private final ITripService tripService;
    private final UserRepository userRepository;

    // ==========================================
    // Đăng chuyến đi (API-055)
    // ==========================================
    @PostMapping("/trips")
    @Operation(summary = "API-055: Đăng chuyến đi", description = "Người giao đăng hành trình tiện đường để nhận ghép đơn")
    public ResponseEntity<ApiResponse<TripResponse>> createTrip(
            @Valid @RequestBody TripCreateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        TripResponse response = tripService.createTrip(request, courierUser);
        return ResponseEntity.ok(ApiResponse.success("Đăng chuyến đi thành công", response));
    }

    // ==========================================
    // Chuyến đi của tôi (API-056)
    // ==========================================
    @GetMapping("/trips")
    @Operation(summary = "API-056: Chuyến đi của tôi", description = "Xem danh sách các chuyến đi của người giao kèm bộ lọc và phân trang")
    public ResponseEntity<ApiResponse<Page<TripResponse>>> getMyTrips(
            @RequestParam(value = "status", required = false) TripStatus status,
            @PageableDefault(size = 10, sort = "departAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        Page<TripResponse> response = tripService.getMyTrips(courierUser, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách chuyến đi thành công", response));
    }

    // ==========================================
    // Chi tiết chuyến đi (API-057)
    // ==========================================
    @GetMapping("/trips/{id}")
    @Operation(summary = "API-057: Chi tiết chuyến đi", description = "Xem chi tiết một chuyến đi cụ thể kèm danh sách đơn ghép")
    public ResponseEntity<ApiResponse<TripResponse>> getTripDetail(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        TripResponse response = tripService.getTripDetail(id, courierUser);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin chuyến đi thành công", response));
    }

    // ==========================================
    // Sửa chuyến đi (API-058)
    // ==========================================
    @PutMapping("/trips/{id}")
    @Operation(summary = "API-058: Sửa chuyến đi", description = "Cập nhật lộ trình, giờ khởi hành, sức chứa hoặc giá sàn của chuyến đi")
    public ResponseEntity<ApiResponse<TripResponse>> updateTrip(
            @PathVariable("id") Long id,
            @Valid @RequestBody TripUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        TripResponse response = tripService.updateTrip(id, request, courierUser);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin chuyến đi thành công", response));
    }

    // ==========================================
    // Tạm dừng / Mở lại nhận đơn (API-059)
    // ==========================================
    @PostMapping("/trips/{id}/pause")
    @Operation(summary = "API-059: Tạm dừng / Mở lại nhận đơn", description = "Chuyển đổi trạng thái tạm dừng nhận đơn cho chuyến đi")
    public ResponseEntity<ApiResponse<TripResponse>> togglePauseTrip(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        TripResponse response = tripService.togglePauseTrip(id, courierUser);
        String msg = response.getStatus() == TripStatus.PAUSED ? "Đã tạm dừng nhận đơn cho chuyến đi" : "Đã mở lại nhận đơn cho chuyến đi";
        return ResponseEntity.ok(ApiResponse.success(msg, response));
    }

    // ==========================================
    // Hủy chuyến đi (API-060)
    // ==========================================
    @PostMapping("/trips/{id}/cancel")
    @Operation(summary = "API-060: Hủy chuyến đi", description = "Hủy một chuyến đi đã lên lịch (chặn hủy nếu đang có đơn ghép theo BR-R-037)")
    public ResponseEntity<ApiResponse<TripResponse>> cancelTrip(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        TripResponse response = tripService.cancelTrip(id, courierUser);
        return ResponseEntity.ok(ApiResponse.success("Hủy chuyến đi thành công", response));
    }

    // ==========================================
    // Kết thúc chuyến đi (API-061)
    // ==========================================
    @PostMapping("/trips/{id}/complete")
    @Operation(summary = "API-061: Kết thúc chuyến đi", description = "Đánh dấu chuyến đi đã hoàn tất hành trình")
    public ResponseEntity<ApiResponse<TripResponse>> completeTrip(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        TripResponse response = tripService.completeTrip(id, courierUser);
        return ResponseEntity.ok(ApiResponse.success("Hoàn tất chuyến đi thành công", response));
    }

    // ==========================================
    // Danh sách đơn hàng đã gắn với chuyến (BR-F-039)
    // ==========================================
    @GetMapping("/trips/{id}/orders")
    @Operation(summary = "Danh sách đơn hàng đã gắn với chuyến đi", description = "Hiển thị các đơn hàng đang được ghép vào chuyến đi")
    public ResponseEntity<ApiResponse<List<TripAttachedOrderResponse>>> getAttachedOrders(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        List<TripAttachedOrderResponse> response = tripService.getAttachedOrders(id, courierUser);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng gắn với chuyến thành công", response));
    }

    // ==========================================
    // Đơn gợi ý cho chuyến đi (API-053)
    // ==========================================
    @GetMapping({"/trips/{id}/suggested-orders", "/matching/orders-for-trip/{id}"})
    @Operation(summary = "API-053: Đơn gợi ý cho chuyến đi", description = "Gợi ý các đơn hàng tiện đường phù hợp với hành trình và sức chứa")
    public ResponseEntity<ApiResponse<List<TripAttachedOrderResponse>>> getSuggestedOrders(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courierUser = resolveUser(jwt);
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        List<TripAttachedOrderResponse> response = tripService.getSuggestedOrders(id, courierUser);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng gợi ý thành công", response));
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
