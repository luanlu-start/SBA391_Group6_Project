package com.fptpost.modules.identity.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.config.AuthenticationCookies;
import com.fptpost.modules.identity.dto.request.*;
import com.fptpost.modules.identity.dto.response.OtpChallengeResponse;
import com.fptpost.modules.identity.service.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/me/contact") @RequiredArgsConstructor
public class ContactController {
    private final ContactService contactService;
    private final AuthenticationCookies cookies;
    @PatchMapping @Operation(summary = "UC-07: Yêu cầu OTP trước khi đổi email/SĐT")
    public ResponseEntity<ApiResponse<OtpChallengeResponse>> request(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ContactChangeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(contactService.request(owner(jwt), session(jwt), request)));
    }
    @PostMapping("/{id}/resend") @Operation(summary = "UC-07: Gửi lại OTP đổi liên hệ")
    public ResponseEntity<ApiResponse<OtpChallengeResponse>> resend(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(contactService.resend(owner(jwt), session(jwt), id)));
    }
    @PostMapping("/verify") @Operation(summary = "UC-07: Xác thực OTP và áp dụng liên hệ mới")
    public ResponseEntity<ApiResponse<Void>> verify(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ContactVerificationRequest request) {
        contactService.verify(owner(jwt), session(jwt), request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Set-Cookie", cookies.clear())
                .body(ApiResponse.success("Đã cập nhật thông tin liên hệ. Vui lòng đăng nhập lại.", null));
    }
    private UUID owner(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private UUID session(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("sid")); }
}
