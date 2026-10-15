package com.fptpost.modules.identity.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.dto.response.UserResponse;
import com.fptpost.modules.identity.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/me") @RequiredArgsConstructor
public class UserController {
    private final AuthenticationService authenticationService;
    @GetMapping @Operation(summary = "Lấy thông tin người dùng đang đăng nhập")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(authenticationService.currentUser(UUID.fromString(jwt.getSubject()))));
    }
}
