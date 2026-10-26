package com.fptpost.modules.identity.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.dto.request.KycSubmissionRequest;
import com.fptpost.modules.identity.dto.response.*;
import com.fptpost.modules.identity.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/v1/me/kyc") @RequiredArgsConstructor
public class KycController {
    private final KycService kycService;
    @GetMapping @Operation(summary = "UC-08: Trạng thái hồ sơ xác minh của tôi")
    public ResponseEntity<ApiResponse<KycStatusResponse>> status(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(kycService.status(owner(jwt))));
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @Operation(summary = "UC-08: Nộp CCCD và ba ảnh đã đồng ý xử lý dữ liệu")
    public ResponseEntity<ApiResponse<KycStatusResponse>> submit(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("request") KycSubmissionRequest request, @RequestPart("front") MultipartFile front,
            @RequestPart("back") MultipartFile back, @RequestPart("portrait") MultipartFile portrait, HttpServletRequest http) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(ApiResponse.created("Đã gửi hồ sơ, chờ kiểm duyệt.",
                kycService.submit(owner(jwt), session(jwt), request, front, back, portrait, http.getRemoteAddr())));
    }
    @PostMapping("/files/{id}/link") @Operation(summary = "UC-08: Cấp liên kết xem ảnh của tôi trong 5 phút")
    public ResponseEntity<ApiResponse<SignedFileLinkResponse>> link(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(kycService.fileLink(owner(jwt), session(jwt), id)));
    }
    @GetMapping("/files/{id}") @Operation(summary = "UC-08: Xem ảnh KYC với JWT sở hữu và chữ ký còn hiệu lực; ghi audit")
    public ResponseEntity<byte[]> file(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam long expires, @RequestParam String signature) {
        var image = kycService.readFile(owner(jwt), session(jwt), id, expires, signature);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(image.mediaType()))
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "default-src 'none'")
                .header("Content-Disposition", "inline; filename=kyc-image").body(image.bytes());
    }
    private UUID owner(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private UUID session(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("sid")); }
}
