package com.group6.project.modules.activity.controller;

import com.group6.project.common.response.ApiResponse;
import com.group6.project.common.response.PageResponse;
import com.group6.project.modules.activity.dto.ActivityLogResponse;
import com.group6.project.modules.activity.dto.ActivityLogSearchRequest;
import com.group6.project.modules.activity.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/activity-logs")
@RequiredArgsConstructor
@Tag(name = "Activity logs", description = "Product activity history stored in MongoDB")
public class ActivityLogController {
    private final ActivityLogService service;

    @GetMapping
    @Operation(summary = "Search activity history", description = "Paginated history with an optional product UUID filter")
    public ResponseEntity<ApiResponse<PageResponse<ActivityLogResponse>>> search(
            @Valid @ModelAttribute @ParameterObject ActivityLogSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Activity logs retrieved successfully", service.search(request)));
    }
}
