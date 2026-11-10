package com.fptpost.modules.payment.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.payment.dto.CourierEarningsResponse;
import com.fptpost.modules.payment.dto.CourierStatsResponse;
import com.fptpost.modules.payment.service.CourierEarningsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/me", "/api/me"})
@RequiredArgsConstructor
@Tag(name = "Courier Earnings & Stats", description = "Xem thu nhập và thống kê hiệu suất người giao (UC-61)")
public class CourierEarningsController {

    private final CourierEarningsService courierEarningsService;
    private final UserRepository userRepository;

    @Operation(summary = "Xem báo cáo thu nhập người giao (UC-61, API-158)",
            description = "Lấy doanh thu gộp, phí hoa hồng nền tảng, thu nhập ròng, biểu đồ biến động và danh sách phiếu thu nhập theo từng đơn.")
    @GetMapping("/earnings")
    public ResponseEntity<ApiResponse<CourierEarningsResponse>> getMyEarnings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false, defaultValue = "MONTH") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long userId = resolveUserId(jwt);
        CourierEarningsResponse response = courierEarningsService.getMyEarnings(userId, period, from, to);
        return ResponseEntity.ok(ApiResponse.success("Lấy báo cáo thu nhập người giao thành công", response));
    }

    @Operation(summary = "Xem chỉ số hiệu suất và tiến độ thăng hạng người giao (UC-61, API-159)",
            description = "Lấy hạng người giao hiện tại, điểm uy tín, tỷ lệ hoàn thành/giao đúng hạn/đánh giá/khiếu nại và số đơn/điểm còn thiếu để lên hạng tiếp theo.")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<CourierStatsResponse>> getMyStats(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = resolveUserId(jwt);
        CourierStatsResponse response = courierEarningsService.getMyStats(userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thống kê hiệu suất người giao thành công", response));
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
}
