package com.fptpost.modules.identity.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.dto.request.UserCreationRequest;
import com.fptpost.modules.identity.dto.response.RegisterResponse;
import com.fptpost.modules.identity.dto.response.RegistrationPoliciesResponse;
import com.fptpost.modules.identity.service.RegistrationPoliciesService;
import com.fptpost.modules.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Authentication", description = "Đăng ký và xác thực tài khoản FPTPost")
public class AuthenticationController {
    UserService userService;
    RegistrationPoliciesService registrationPoliciesService;

    @GetMapping("/registration-policies")
    @Operation(summary = "Lấy nội dung và phiên bản chính sách đăng ký hiện tại")
    public ResponseEntity<ApiResponse<RegistrationPoliciesResponse>> getRegistrationPolicies() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(registrationPoliciesService.getPolicies()));
    }

    @PostMapping("/register")
    @Operation(summary = "UC-01: Đăng ký tài khoản chờ xác thực và gửi OTP email")
    public ResponseEntity<ApiResponse<RegisterResponse>> createUser(
            @Valid @RequestBody UserCreationRequest request, HttpServletRequest httpRequest) {
        var result = userService.createUser(request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.created("Đăng ký thành công. Vui lòng kiểm tra email xác thực.",
                        result));
    }
}
