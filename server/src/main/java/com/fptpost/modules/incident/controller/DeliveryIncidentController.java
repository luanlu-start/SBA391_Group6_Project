package com.fptpost.modules.incident.controller;

import com.fptpost.common.exception.*;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.incident.dto.*;
import com.fptpost.modules.incident.service.DeliveryIncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/orders/{orderId}/incidents")
public class DeliveryIncidentController {
    private final DeliveryIncidentService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('COURIER')")
    public ResponseEntity<ApiResponse<IncidentResponse>> report(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId, @Valid @RequestPart("request") IncidentRequest request,
            @RequestPart(name = "photo", required = false) MultipartFile photo) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.created("Đã ghi nhận sự cố giao hàng", service.report(actor(jwt), orderId, request, photo)));
    }

    @GetMapping @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> history(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(PageResponse.from(service.history(actor(jwt), orderId, page, size))));
    }

    @GetMapping("/{incidentId}/evidence") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> evidence(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId,
                                         @PathVariable Long incidentId) {
        var image = service.evidence(actor(jwt), orderId, incidentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.mediaType()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'").body(image.bytes());
    }

    private UUID actor(Jwt jwt) {
        try {
            if (jwt == null) throw new IllegalArgumentException();
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
    }
}
