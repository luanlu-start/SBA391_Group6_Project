package com.fptpost.modules.jobs.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.jobs.dto.response.JobRunResponse;
import com.fptpost.modules.jobs.dto.response.JobStatusResponse;
import com.fptpost.modules.jobs.service.JobAdministrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/jobs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Scheduled jobs", description = "UC-63 giám sát và chạy thủ công tác vụ định kỳ")
public class AdminJobController {

    private final JobAdministrationService jobAdministrationService;

    @GetMapping
    @Operation(summary = "Trạng thái các tác vụ định kỳ (API-162)")
    public ResponseEntity<ApiResponse<List<JobStatusResponse>>> list() {
        return json(jobAdministrationService.list());
    }

    @GetMapping("/{name}/runs")
    @Operation(summary = "Nhật ký chạy của một tác vụ")
    public ResponseEntity<ApiResponse<PageResponse<JobRunResponse>>> history(@PathVariable String name,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return json(jobAdministrationService.history(name, page, size));
    }

    @PostMapping("/{name}/run")
    @Operation(summary = "Chạy thủ công một tác vụ (API-163)")
    public ResponseEntity<ApiResponse<JobRunResponse>> run(@AuthenticationPrincipal Jwt jwt, @PathVariable String name) {
        return json(jobAdministrationService.runNow(UUID.fromString(jwt.getSubject()), name));
    }

    private <T> ResponseEntity<ApiResponse<T>> json(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }
}
