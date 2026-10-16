package com.fptpost.modules.identity.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.dto.request.UserCreationRequest;
import com.fptpost.modules.identity.dto.request.OtpVerificationRequest;
import com.fptpost.modules.identity.dto.request.OtpResendRequest;
import com.fptpost.modules.identity.dto.response.OtpVerificationResponse;
import com.fptpost.modules.identity.dto.response.OtpChallengeResponse;
import com.fptpost.modules.identity.dto.response.RegisterResponse;
import com.fptpost.modules.identity.dto.response.RegistrationPoliciesResponse;
import com.fptpost.modules.identity.service.PolicyService;
import com.fptpost.modules.identity.service.UserService;
import com.fptpost.modules.identity.service.OtpService;
import com.fptpost.modules.identity.service.AuthenticationService;
import com.fptpost.modules.identity.config.AuthenticationCookies;
import com.fptpost.modules.identity.dto.request.AuthenticationRequest;
import com.fptpost.modules.identity.dto.response.AuthenticationResponse;
import com.fptpost.modules.identity.dto.response.CsrfTokenResponse;
import com.fptpost.modules.identity.dto.response.SessionResponse;
import com.fptpost.modules.identity.dto.response.SessionRevocationResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import java.util.UUID;
import java.util.List;
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
    PolicyService policyService;
    OtpService otpService;
    AuthenticationService authenticationService;
    AuthenticationCookies cookies;

    @GetMapping("/csrf")
    @Operation(summary = "Lấy CSRF token trước khi đăng nhập/làm mới/đăng xuất")
    public ResponseEntity<ApiResponse<CsrfTokenResponse>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(
                CsrfTokenResponse.builder().headerName(token.getHeaderName()).token(token.getToken()).build()));
    }

    @PostMapping("/login")
    @Operation(summary = "UC-03: Đăng nhập bằng email hoặc SĐT; refresh token qua cookie HttpOnly")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> login(@Valid @RequestBody AuthenticationRequest request,
            @CookieValue(name = AuthenticationCookies.BROWSER_COOKIE, required = false) String browserCookie,
            @CookieValue(name = AuthenticationCookies.REFRESH_COOKIE, required = false) String previousRefreshToken,
            HttpServletRequest httpRequest) {
        var result = authenticationService.authenticate(request, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"),
                cookies.resolveBrowserId(browserCookie), previousRefreshToken);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Set-Cookie",
                        cookies.issue(result.getRefreshToken(), result.isRememberMe()), cookies.issueBrowser(result.getBrowserId()))
                .body(ApiResponse.success("Đăng nhập thành công.", result.getResponse()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "UC-03: Làm mới access token và xoay vòng refresh cookie")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> refresh(
            @CookieValue(name = AuthenticationCookies.REFRESH_COOKIE, required = false) String token,
            @CookieValue(name = AuthenticationCookies.BROWSER_COOKIE, required = false) String browserCookie,
            HttpServletRequest request) {
        var result = authenticationService.refresh(token, request.getRemoteAddr(), request.getHeader("User-Agent"),
                cookies.resolveBrowserId(browserCookie));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Set-Cookie",
                        cookies.issue(result.getRefreshToken(), result.isRememberMe()), cookies.issueBrowser(result.getBrowserId()))
                .body(ApiResponse.success(result.getResponse()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất phiên hiện tại")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal Jwt jwt) {
        authenticationService.logout(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("sid")));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Set-Cookie", cookies.clear())
                .body(ApiResponse.success("Đã đăng xuất.", null));
    }

    @GetMapping("/registration-policies")
    @Operation(summary = "Lấy nội dung và phiên bản chính sách đăng ký hiện tại")
    public ResponseEntity<ApiResponse<RegistrationPoliciesResponse>> getRegistrationPolicies() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(policyService.getPolicies()));
    }

    @GetMapping("/sessions")
    @Operation(summary = "UC-04: Xem các phiên đang hoạt động của tài khoản hiện tại")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> sessions(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(
                authenticationService.sessions(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("sid")))));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "UC-04: Đăng xuất một phiên của tài khoản hiện tại")
    public ResponseEntity<ApiResponse<SessionRevocationResponse>> revokeSession(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        boolean current = authenticationService.revokeSession(UUID.fromString(jwt.getSubject()), id,
                UUID.fromString(jwt.getClaimAsString("sid")));
        var response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (current) response.header("Set-Cookie", cookies.clear());
        return response.body(ApiResponse.success("Đã đăng xuất phiên được chọn.",
                SessionRevocationResponse.builder().currentSessionRevoked(current).build()));
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

    @PostMapping("/otp/verify")
    @Operation(summary = "UC-02: Xác thực OTP email và kích hoạt tài khoản")
    public ResponseEntity<ApiResponse<OtpVerificationResponse>> verifyOtp(
            @Valid @RequestBody OtpVerificationRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Xác thực email thành công.", otpService.verifyOtp(request)));
    }

    @PostMapping("/otp/resend")
    @Operation(summary = "UC-02: Gửi lại OTP email sau thời gian chờ")
    public ResponseEntity<ApiResponse<OtpChallengeResponse>> resendOtp(
            @Valid @RequestBody OtpResendRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Đã gửi mã xác thực mới.", otpService.resendOtp(request.getChallengeId())));
    }
}
