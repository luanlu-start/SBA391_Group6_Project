package com.fptpost.modules.payment.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.payment.dto.LedgerIntegrityResponse;
import com.fptpost.modules.payment.dto.ReconciliationAdjustRequest;
import com.fptpost.modules.payment.dto.ReconciliationItemResponse;
import com.fptpost.modules.payment.dto.ReconciliationReportResponse;
import com.fptpost.modules.payment.dto.ReconciliationRunRequest;
import com.fptpost.modules.payment.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Controller đối soát giao dịch hằng ngày và kiểm tra bất biến sổ cái cho Admin (UC-43, API-106 -> API-109).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Reconciliation & Ledger", description = "Đối soát giao dịch hằng ngày và kiểm tra bất biến sổ cái kép (UC-43)")
public class AdminReconciliationController {

    private final ReconciliationService reconciliationService;
    private final UserRepository userRepository;

    @Operation(summary = "Danh sách báo cáo đối soát giao dịch (UC-43, API-106)",
            description = "Lấy danh sách phân trang các báo cáo đối soát giao dịch hằng ngày kèm chi tiết từng dòng khớp/lệch.")
    @GetMapping("/reconciliation")
    public ResponseEntity<ApiResponse<PageResponse<ReconciliationReportResponse>>> getReports(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        PageResponse<ReconciliationReportResponse> response = reconciliationService.getReports(page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách báo cáo đối soát thành công", response));
    }

    @Operation(summary = "Xem chi tiết một báo cáo đối soát (UC-43)",
            description = "Lấy chi tiết báo cáo đối soát theo ID và toàn bộ các dòng giao dịch khớp/lệch.")
    @GetMapping("/reconciliation/{id}")
    public ResponseEntity<ApiResponse<ReconciliationReportResponse>> getReportById(
            @PathVariable("id") Long id) {
        ReconciliationReportResponse response = reconciliationService.getReportById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết báo cáo đối soát thành công", response));
    }

    @Operation(summary = "Chạy đối soát giao dịch thủ công (UC-43, API-107)",
            description = "Admin kích hoạt chạy đối soát giao dịch theo ngày chọn. Hệ thống cảnh báo dòng lệch và không tự ý điều chỉnh số dư ví.")
    @PostMapping("/reconciliation/run")
    public ResponseEntity<ApiResponse<ReconciliationReportResponse>> runReconciliation(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "reportDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate queryDate,
            @RequestBody(required = false) ReconciliationRunRequest body) {
        Long adminUserId = resolveUserId(jwt);
        LocalDate targetDate = (body != null && body.getReportDate() != null) ? body.getReportDate() : queryDate;
        log.info("Admin {} triggering manual reconciliation for date={}", adminUserId, targetDate);
        ReconciliationReportResponse response = reconciliationService.runReconciliation(targetDate, adminUserId);
        return ResponseEntity.ok(ApiResponse.success("Chạy đối soát giao dịch thành công", response));
    }

    @Operation(summary = "Điều chỉnh dòng lệch bằng bút toán điều chỉnh (UC-43, API-108)",
            description = "Admin xử lý dòng giao dịch lệch bằng cách tạo bút toán điều chỉnh bất biến trên sổ cái kép.")
    @PostMapping("/reconciliation/items/{id}/adjust")
    public ResponseEntity<ApiResponse<ReconciliationItemResponse>> adjustItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id,
            @Valid @RequestBody ReconciliationAdjustRequest request) {
        Long adminUserId = resolveUserId(jwt);
        log.info("Admin {} adjusting reconciliation item id={}", adminUserId, id);
        ReconciliationItemResponse response = reconciliationService.adjustMismatchItem(id, request, adminUserId);
        return ResponseEntity.ok(ApiResponse.success("Tạo bút toán điều chỉnh dòng lệch thành công", response));
    }

    @Operation(summary = "Kiểm tra bất biến sổ cái kép (UC-43, API-109)",
            description = "Kiểm tra tính cân bằng Tổng Nợ == Tổng Có trên toàn bộ sổ cái và từng giao dịch.")
    @GetMapping("/ledger/integrity")
    public ResponseEntity<ApiResponse<LedgerIntegrityResponse>> checkLedgerIntegrity() {
        LedgerIntegrityResponse response = reconciliationService.checkLedgerIntegrity();
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    private Long resolveUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        try {
            UUID publicId = UUID.fromString(jwt.getSubject());
            return userRepository.findByPublicId(publicId)
                    .map(User::getId)
                    .orElseThrow(() -> new AppException(GlobalErrorCode.UNAUTHORIZED));
        } catch (IllegalArgumentException e) {
            try {
                return Long.parseLong(jwt.getSubject());
            } catch (NumberFormatException nfe) {
                throw new AppException(GlobalErrorCode.UNAUTHORIZED);
            }
        }
    }
}
