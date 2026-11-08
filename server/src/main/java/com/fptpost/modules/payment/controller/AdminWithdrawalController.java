package com.fptpost.modules.payment.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.payment.dto.*;
import com.fptpost.modules.payment.service.AdminWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/withdrawals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin withdrawals", description = "UC-41 duyệt hai cấp và chi trả yêu cầu rút tiền")
public class AdminWithdrawalController {

    private final AdminWithdrawalService adminWithdrawalService;

    @GetMapping
    @Operation(summary = "Hàng đợi rút tiền (API-100)")
    public ResponseEntity<ApiResponse<PageResponse<AdminWithdrawalResponse>>> list(
            @AuthenticationPrincipal Jwt jwt, @Valid @ModelAttribute @ParameterObject AdminWithdrawalSearchRequest request) {
        return json(adminWithdrawalService.list(actor(jwt), request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết yêu cầu rút kèm thông tin đối chiếu ví")
    public ResponseEntity<ApiResponse<AdminWithdrawalResponse>> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return json(adminWithdrawalService.detail(actor(jwt), id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Duyệt cấp 1 hoặc cấp 2 (API-101)")
    public ResponseEntity<ApiResponse<AdminWithdrawalResponse>> approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return json(adminWithdrawalService.approve(actor(jwt), id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Từ chối kèm lý do, hoàn tiền về ví khả dụng (API-102)")
    public ResponseEntity<ApiResponse<AdminWithdrawalResponse>> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                                       @Valid @RequestBody WithdrawalReasonRequest request) {
        return json(adminWithdrawalService.reject(actor(jwt), id, request));
    }

    @PostMapping("/{id}/mark-paid")
    @Operation(summary = "Nhập mã tham chiếu chi thủ công (API-103)")
    public ResponseEntity<ApiResponse<AdminWithdrawalResponse>> markPaid(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                                         @Valid @RequestBody WithdrawalPaidRequest request) {
        return json(adminWithdrawalService.markPaid(actor(jwt), id, request));
    }

    @PostMapping("/{id}/mark-failed")
    @Operation(summary = "Ghi nhận chi thất bại, hoàn tiền về ví khả dụng")
    public ResponseEntity<ApiResponse<AdminWithdrawalResponse>> markFailed(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                                           @Valid @RequestBody WithdrawalReasonRequest request) {
        return json(adminWithdrawalService.markFailed(actor(jwt), id, request));
    }

    @PostMapping("/export")
    @Operation(summary = "Xuất lệnh chi hàng loạt dạng CSV và chuyển các yêu cầu sang Đang chi (API-104)")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody(required = false) WithdrawalExportRequest request) {
        WithdrawalPayoutFile file = adminWithdrawalService.export(actor(jwt), request == null ? null : request.getIds());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .header("X-Export-Count", Integer.toString(file.count()))
                .body(file.content());
    }

    @PostMapping(path = "/import-result", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Nhập kết quả chi hàng loạt từ CSV (API-105)")
    public ResponseEntity<ApiResponse<WithdrawalImportResultResponse>> importResult(@AuthenticationPrincipal Jwt jwt,
                                                                                    @RequestPart("file") MultipartFile file) {
        return json(adminWithdrawalService.importResults(actor(jwt), file));
    }

    private <T> ResponseEntity<ApiResponse<T>> json(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
