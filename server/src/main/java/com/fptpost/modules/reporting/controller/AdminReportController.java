package com.fptpost.modules.reporting.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.reporting.dto.response.CourierRevenueResponse;
import com.fptpost.modules.reporting.dto.response.DashboardResponse;
import com.fptpost.modules.reporting.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin reports", description = "UC-59 dashboard điều hành và báo cáo")
public class AdminReportController {

    private final ReportingService reportingService;

    @GetMapping("/dashboard")
    @Operation(summary = "Số liệu dashboard theo khoảng ngày giờ Việt Nam; mặc định 30 ngày gần nhất (API-154)")
    public ResponseEntity<ApiResponse<DashboardResponse>> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return json(reportingService.dashboard(from, to));
    }

    @GetMapping("/reports/courier-revenue")
    @Operation(summary = "Doanh thu theo người giao trong tháng yyyy-MM (API-156)")
    public ResponseEntity<ApiResponse<List<CourierRevenueResponse>>> courierRevenue(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return json(reportingService.courierRevenue(month));
    }

    @GetMapping("/reports/export")
    @Operation(summary = "Xuất CSV UTF-8 mở được bằng Excel: type = ORDERS, DASHBOARD (from/to) hoặc COURIER_REVENUE (month) (API-155)")
    public ResponseEntity<byte[]> export(@RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        ReportingService.ExportFile file = reportingService.export(type, from, to, month);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    private <T> ResponseEntity<ApiResponse<T>> json(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }
}
