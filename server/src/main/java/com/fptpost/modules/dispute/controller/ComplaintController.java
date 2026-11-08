package com.fptpost.modules.dispute.controller;

import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.dispute.dto.request.ComplaintAppealRequest;
import com.fptpost.modules.dispute.dto.request.ComplaintMessageRequest;
import com.fptpost.modules.dispute.dto.response.ComplaintDetailResponse;
import com.fptpost.modules.dispute.dto.response.ComplaintSummaryResponse;
import com.fptpost.modules.dispute.service.ComplaintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
@Tag(name = "Complaints", description = "UC-51 khiếu nại của các bên: xem hồ sơ, trao đổi, kháng nghị")
public class ComplaintController {

    private final ComplaintService complaintService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Khiếu nại liên quan đến đơn của tôi (API-123)")
    public ResponseEntity<ApiResponse<PageResponse<ComplaintSummaryResponse>>> mine(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return json(complaintService.mine(actor(jwt), page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiết khiếu nại cho các bên của đơn, kiểm duyệt viên và Admin (API-124)")
    public ResponseEntity<ApiResponse<ComplaintDetailResponse>> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return json(complaintService.detail(actor(jwt), id));
    }

    @PostMapping(path = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Gửi trao đổi kèm tối đa 3 ảnh bằng chứng (API-125)")
    public ResponseEntity<ApiResponse<ComplaintDetailResponse>> message(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestPart(name = "request", required = false) ComplaintMessageRequest request,
            @RequestPart(name = "files", required = false) List<MultipartFile> files) {
        return json(complaintService.postMessage(actor(jwt), id, request, files));
    }

    @GetMapping("/{id}/evidence/{evidenceId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ảnh bằng chứng của khiếu nại")
    public ResponseEntity<byte[]> evidence(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID evidenceId) {
        ValidatedImage image = complaintService.evidence(actor(jwt), id, evidenceId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(image.mediaType()))
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "default-src 'none'")
                .body(image.bytes());
    }

    @PostMapping("/{id}/appeal")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kháng nghị phán quyết một lần trong thời hạn kháng nghị (API-127)")
    public ResponseEntity<ApiResponse<ComplaintDetailResponse>> appeal(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                                       @Valid @RequestBody ComplaintAppealRequest request) {
        return json(complaintService.appeal(actor(jwt), id, request));
    }

    private <T> ResponseEntity<ApiResponse<T>> json(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
