package com.fptpost.modules.dispute.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.dispute.dto.request.AppealReviewRequest;
import com.fptpost.modules.dispute.dto.request.ComplaintInfoRequest;
import com.fptpost.modules.dispute.dto.request.ComplaintRulingRequest;
import com.fptpost.modules.dispute.dto.response.ComplaintChatHistory;
import com.fptpost.modules.dispute.dto.response.ComplaintDetailResponse;
import com.fptpost.modules.dispute.dto.response.ComplaintSummaryResponse;
import com.fptpost.modules.dispute.service.ComplaintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Complaint review", description = "UC-51 hàng đợi khiếu nại, phán quyết và xem xét kháng nghị")
public class ModeratorComplaintController {

    private final ComplaintService complaintService;

    @GetMapping("/mod/complaints")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Hàng đợi khiếu nại theo hạn xử lý (API-123 theo vai trò kiểm duyệt)")
    public ResponseEntity<ApiResponse<PageResponse<ComplaintSummaryResponse>>> queue(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return json(complaintService.queue(actor(jwt), status, mine, page, size));
    }

    @GetMapping("/mod/complaints/{id}/chat")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Lịch sử chat của đơn, chỉ đọc")
    public ResponseEntity<ApiResponse<ComplaintChatHistory>> chat(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return json(complaintService.chat(actor(jwt), id));
    }

    @PostMapping("/mod/complaints/{id}/claim")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Tiếp nhận khiếu nại; Admin có thể tiếp quản hồ sơ đang xử lý")
    public ResponseEntity<ApiResponse<ComplaintDetailResponse>> claim(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return json(complaintService.claim(actor(jwt), id));
    }

    @PostMapping("/mod/complaints/{id}/request-info")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Yêu cầu các bên bổ sung bằng chứng (A2)")
    public ResponseEntity<ApiResponse<ComplaintDetailResponse>> requestInfo(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                                            @Valid @RequestBody ComplaintInfoRequest request) {
        return json(complaintService.requestInfo(actor(jwt), id, request));
    }

    @PostMapping("/mod/complaints/{id}/ruling")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Phán quyết kèm lý do; tiền được xử lý khi hết hạn kháng nghị (API-126)")
    public ResponseEntity<ApiResponse<ComplaintDetailResponse>> rule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                                     @Valid @RequestBody ComplaintRulingRequest request) {
        return json(complaintService.rule(actor(jwt), id, request));
    }

    @PostMapping("/admin/complaints/{id}/appeal-review")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin giữ nguyên hoặc sửa phán quyết bị kháng nghị, sau đó xử lý tiền (A1)")
    public ResponseEntity<ApiResponse<ComplaintDetailResponse>> reviewAppeal(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                                             @Valid @RequestBody AppealReviewRequest request) {
        return json(complaintService.reviewAppeal(actor(jwt), id, request));
    }

    private <T> ResponseEntity<ApiResponse<T>> json(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
