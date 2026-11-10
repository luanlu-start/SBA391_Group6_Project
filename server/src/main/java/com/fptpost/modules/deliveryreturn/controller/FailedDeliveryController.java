package com.fptpost.modules.deliveryreturn.controller;

import com.fptpost.common.exception.*;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.deliveryreturn.dto.*;
import com.fptpost.modules.deliveryreturn.service.FailedDeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/orders/{orderId}") @RequiredArgsConstructor
public class FailedDeliveryController {
    private final FailedDeliveryService service;
    @GetMapping("/return") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReturnResponse>> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
        return json(service.detail(actor(jwt), orderId));
    }
    @PostMapping("/delivery-contact-attempts") @PreAuthorize("hasRole('COURIER')")
    public ResponseEntity<ApiResponse<ReturnResponse>> contact(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId,
                                                             @Valid @RequestBody ReturnRequests.Contact input) {
        return json(service.contact(actor(jwt), orderId, input));
    }
    @PostMapping(path = "/delivery-failed", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasRole('COURIER')")
    public ResponseEntity<ApiResponse<ReturnResponse>> report(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId,
            @Valid @RequestPart("request") ReturnRequests.Failure input, @RequestPart("photo") MultipartFile photo) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.created("Đã ghi nhận giao thất bại và bắt đầu hoàn hàng", service.report(actor(jwt), orderId, input, photo)));
    }
    @PostMapping("/return/resume") @PreAuthorize("hasRole('COURIER')")
    public ResponseEntity<ApiResponse<ReturnResponse>> resume(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId,
                                                            @Valid @RequestBody ReturnRequests.Resume input) {
        return json(service.resume(actor(jwt), orderId, input));
    }
    @PostMapping("/return/code") @PreAuthorize("hasRole('SENDER')")
    public ResponseEntity<ApiResponse<ReturnResponse.Code>> code(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
        return json(service.issueCode(actor(jwt), orderId));
    }
    @PostMapping("/return/confirm") @PreAuthorize("hasRole('SENDER')")
    public ResponseEntity<ApiResponse<ReturnResponse>> confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId,
                                                             @Valid @RequestBody ReturnRequests.Confirm input) {
        return json(service.confirm(actor(jwt), orderId, input));
    }
    @GetMapping("/return/{caseId}/evidence") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> evidence(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId, @PathVariable Long caseId) {
        var image = service.evidence(actor(jwt), orderId, caseId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(image.mediaType()))
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "default-src 'none'").body(image.bytes());
    }
    private <T> ResponseEntity<ApiResponse<T>> json(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(value)); }
    private UUID actor(Jwt jwt) {
        try { if (jwt == null) throw new IllegalArgumentException(); return UUID.fromString(jwt.getSubject()); }
        catch (IllegalArgumentException | NullPointerException exception) { throw new AppException(GlobalErrorCode.UNAUTHORIZED); }
    }
}
