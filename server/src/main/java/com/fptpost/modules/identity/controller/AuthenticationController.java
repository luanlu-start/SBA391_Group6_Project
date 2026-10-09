package com.fptpost.modules.identity.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.dto.*;
import com.fptpost.modules.identity.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Identity", description = "Đăng ký tài khoản FPTPost")
public class AuthenticationController {
    private final RegistrationService registration;
    private final RegistrationPoliciesService policies;

    @GetMapping("/registration-policies")
    @Operation(summary = "Lấy nội dung và phiên bản chính sách đăng ký hiện tại")
    public ResponseEntity<ApiResponse<RegistrationPoliciesResponse>> policies() {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(ApiResponse.success(policies.getPolicies()));
    }

    @PostMapping("/register")
    @Operation(summary = "UC-01: Đăng ký tài khoản chờ xác thực và gửi OTP email")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.status(201).header("Cache-Control", "no-store")
                .body(ApiResponse.created("Đăng ký thành công. Vui lòng kiểm tra email xác thực.",
                        registration.register(request, httpRequest.getRemoteAddr())));
    }
}
