package com.fptpost.modules.moderation.controller;

import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.moderation.dto.request.ViolationReportRequest;
import com.fptpost.modules.moderation.dto.request.ViolationResolveRequest;
import com.fptpost.modules.moderation.dto.response.MyViolationReportResponse;
import com.fptpost.modules.moderation.dto.response.ViolationCaseResponse;
import com.fptpost.modules.moderation.service.ViolationReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Violation reports", description = "UC-55 báo cáo vi phạm và hàng đợi xử lý")
public class ViolationReportController {

    private final ViolationReportService violationReportService;

    @PostMapping(path = "/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Báo cáo đơn, người dùng hoặc tin nhắn kèm tối đa 3 ảnh bằng chứng; báo cáo trùng được gộp (API-139)")
    public ResponseEntity<ApiResponse<MyViolationReportResponse>> submit(@AuthenticationPrincipal Jwt jwt,
                                                                         @Valid @RequestPart("request") ViolationReportRequest request,
                                                                         @RequestPart(name = "files", required = false) List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.created("Đã ghi nhận báo cáo vi phạm", violationReportService.submit(actor(jwt), request, files)));
    }

    @GetMapping("/me/reports")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Báo cáo vi phạm tôi đã gửi")
    public ResponseEntity<ApiResponse<PageResponse<MyViolationReportResponse>>> mine(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return json(violationReportService.mine(actor(jwt), page, size));
    }

    @GetMapping("/mod/reports")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Hàng đợi hồ sơ vi phạm, nhiều báo cáo hiển thị trước (API-140)")
    public ResponseEntity<ApiResponse<PageResponse<ViolationCaseResponse>>> queue(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status, @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return json(violationReportService.queue(actor(jwt), status, targetType, page, size));
    }

    @GetMapping("/mod/reports/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Chi tiết hồ sơ vi phạm và các báo cáo đã gộp")
    public ResponseEntity<ApiResponse<ViolationCaseResponse>> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return json(violationReportService.detail(actor(jwt), id));
    }

    @GetMapping("/mod/reports/{id}/evidence/{evidenceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Ảnh bằng chứng của hồ sơ")
    public ResponseEntity<byte[]> evidence(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        ValidatedImage image = violationReportService.evidence(id, evidenceId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(image.mediaType()))
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "default-src 'none'")
                .body(image.bytes());
    }

    @PostMapping("/mod/reports/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Cảnh cáo, đề nghị Admin khóa hoặc bác báo cáo")
    public ResponseEntity<ApiResponse<ViolationCaseResponse>> resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                                      @Valid @RequestBody ViolationResolveRequest request) {
        return json(violationReportService.resolve(actor(jwt), id, request));
    }

    private <T> ResponseEntity<ApiResponse<T>> json(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
