package com.fptpost.modules.trip.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.trip.dto.request.TripCreateRequest;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
@Tag(name = "Trips", description = "Quản lý Chuyến đi & Ghép đơn tiện đường (TRP) - UC-22, UC-23")
public class TripController {

    private final ITripService tripService;
    private final UserRepository userRepository;

    // ==========================================
    // UC-22: Đăng chuyến đi (API-055)
    // ==========================================
    @PostMapping
    @Operation(summary = "API-055: Đăng chuyến đi (UC-22)", description = "Người giao đăng hành trình tiện đường để nhận ghép đơn")
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
    // UC-22: Chuyến đi của tôi (API-056)
    // ==========================================
    @GetMapping
    @Operation(summary = "API-056: Chuyến đi của tôi (UC-22)", description = "Xem danh sách các chuyến đi của người giao kèm bộ lọc và phân trang")
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
    @GetMapping("/{id}")
    @Operation(summary = "API-057: Chi tiết chuyến đi", description = "Xem chi tiết một chuyến đi cụ thể")
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
    // Hủy chuyến đi (API-060)
    // ==========================================
    @PostMapping("/{id}/cancel")
    @Operation(summary = "API-060: Hủy chuyến đi", description = "Hủy một chuyến đi đã lên lịch")
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
