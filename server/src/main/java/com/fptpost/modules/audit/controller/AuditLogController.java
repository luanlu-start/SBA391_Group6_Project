package com.fptpost.modules.audit.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.audit.dto.request.AuditLogSearchRequest;
import com.fptpost.modules.audit.dto.response.AuditLogResponse;
import com.fptpost.modules.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit logs", description = "UC-60 read-only administrative audit lookup")
public class AuditLogController {
    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "Search immutable audit events (ADMIN only)", description = "Exact actor/action/entity filters "
            + "combined with inclusive ISO instant boundaries. Page starts at 0, maximum 100000; size 1 to 100. "
            + "Fixed order: at DESC, eventId DESC. before/after are masked again before being returned.")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> search(
            @Valid @ModelAttribute @ParameterObject AuditLogSearchRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(auditLogService.search(request)));
    }
}
