package com.fptpost.modules.identity.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.config.AuthenticationCookies;
import com.fptpost.modules.identity.dto.request.CourierRegistrationRequest;
import com.fptpost.modules.identity.dto.response.*;
import com.fptpost.modules.identity.service.CourierService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/me/courier-registration") @RequiredArgsConstructor
public class CourierController {
    private final CourierService courierService;
    private final AuthenticationCookies cookies;
    @GetMapping @Operation(summary = "UC-10: Điều kiện đăng ký và hồ sơ người giao của tôi")
    public ResponseEntity<ApiResponse<CourierRegistrationStatusResponse>> status(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(courierService.status(UUID.fromString(jwt.getSubject()))));
    }
    @PostMapping @Operation(summary = "UC-10: Đăng ký vai trò người giao sau khi KYC được duyệt")
    public ResponseEntity<ApiResponse<CourierRegistrationResponse>> register(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CourierRegistrationRequest input, HttpServletRequest http) {
        var result = courierService.register(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("sid")), input, http.getRemoteAddr(), http.getHeader("User-Agent"));
        var auth = result.getAuthentication();
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).header("Set-Cookie", cookies.issue(auth.getRefreshToken(), auth.isRememberMe()), cookies.issueBrowser(auth.getBrowserId()))
                .body(ApiResponse.created("Đăng ký người giao thành công.", CourierRegistrationResponse.builder().profile(result.getProfile()).authentication(auth.getResponse()).build()));
    }
}
