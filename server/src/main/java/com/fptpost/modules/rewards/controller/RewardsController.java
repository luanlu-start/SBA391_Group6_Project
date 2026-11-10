package com.fptpost.modules.rewards.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.rewards.dto.BadgesResponse;
import com.fptpost.modules.rewards.dto.ReferralResponse;
import com.fptpost.modules.rewards.dto.ReferralCodeRequest;
import com.fptpost.modules.rewards.dto.ReferralDetailResponse;
import jakarta.validation.Valid;
import com.fptpost.modules.rewards.service.RewardsService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class RewardsController {
    private final RewardsService rewards;

    @GetMapping("/badges")
    @Operation(summary = "UC-62 (API-160): Huy hiệu của tôi và tiến độ giao hàng trong tuần")
    public ResponseEntity<ApiResponse<BadgesResponse>> badges(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(rewards.badges(UUID.fromString(jwt.getSubject()))));
    }

    @GetMapping("/referral")
    @Operation(summary = "UC-62 (API-161): Mã giới thiệu và thống kê thưởng của tôi")
    public ResponseEntity<ApiResponse<ReferralResponse>> referral(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(rewards.referral(UUID.fromString(jwt.getSubject()), page, size)));
    }

    @PostMapping("/referral")
    @Operation(summary = "UC-62: Nhập mã giới thiệu một lần trước khi hoàn tất đơn đầu tiên")
    public ResponseEntity<ApiResponse<ReferralDetailResponse>> applyReferral(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReferralCodeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Đã ghi nhận mã giới thiệu.",
                        rewards.applyReferral(UUID.fromString(jwt.getSubject()), request.referralCode())));
    }
}
