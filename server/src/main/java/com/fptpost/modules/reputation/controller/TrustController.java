package com.fptpost.modules.reputation.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.reputation.dto.request.RestrictCourierRequest;
import com.fptpost.modules.reputation.dto.response.CourierTrustResponse;
import com.fptpost.modules.reputation.dto.response.MyTrustResponse;
import com.fptpost.modules.reputation.service.TrustScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Trust score", description = "UC-49 điểm uy tín và hạng người giao")
public class TrustController {

    private final TrustScoreService trustScoreService;

    @GetMapping("/me/trust")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Điểm uy tín, hạng, quyền lợi và tiến độ lên hạng của người giao (API-121)")
    public ResponseEntity<ApiResponse<MyTrustResponse>> mine(@AuthenticationPrincipal Jwt jwt) {
        return json(trustScoreService.mine(UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/admin/trust")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Người giao theo điểm uy tín tăng dần; mặc định chỉ người dưới ngưỡng xem xét khóa")
    public ResponseEntity<ApiResponse<PageResponse<CourierTrustResponse>>> couriers(@RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return json(trustScoreService.couriers(filter, page, size));
    }

    @PostMapping("/admin/trust/{courierId}/restrict")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạm khóa nhận đơn sau khi xem xét người giao điểm thấp (BR-R-074)")
    public ResponseEntity<ApiResponse<CourierTrustResponse>> restrict(@PathVariable UUID courierId,
                                                                      @Valid @RequestBody RestrictCourierRequest request) {
        return json(trustScoreService.restrict(courierId, request));
    }

    @PostMapping("/admin/trust/recalculate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tính lại điểm toàn bộ người giao ngay, không chờ lượt hằng đêm")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> recalculate() {
        return json(Map.of("couriers", trustScoreService.recalculateAllNow()));
    }

    private <T> ResponseEntity<ApiResponse<T>> json(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }
}
