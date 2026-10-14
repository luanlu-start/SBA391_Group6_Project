package com.fptpost.modules.catalog.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.catalog.dto.request.ParameterBatchUpdateRequest;
import com.fptpost.modules.catalog.dto.request.ParameterHistoryRequest;
import com.fptpost.modules.catalog.dto.request.ParameterUpdateRequest;
import com.fptpost.modules.catalog.dto.response.ParameterHistoryResponse;
import com.fptpost.modules.catalog.dto.response.ParameterResponse;
import com.fptpost.modules.catalog.service.ParameterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/params")
@RequiredArgsConstructor
@Tag(name = "System parameters", description = "UC-46 administrator parameter catalog and history")
public class ParameterController {
    private final ParameterService parameterService;

    @GetMapping
    @Operation(summary = "List the current 109 system parameters (ADMIN only)")
    public ResponseEntity<ApiResponse<List<ParameterResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(parameterService.list()));
    }

    @PutMapping("/batch")
    @Operation(summary = "Update related parameters atomically (ADMIN only)",
            description = "Additive UC-46 contract for changes such as reputation weights whose total must stay 1")
    public ResponseEntity<ApiResponse<List<ParameterResponse>>> updateBatch(
            @Valid @RequestBody ParameterBatchUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(parameterService.updateBatch(request)));
    }

    @PutMapping("/{key}")
    @Operation(summary = "Update a parameter using its expected version (ADMIN only)")
    public ResponseEntity<ApiResponse<ParameterResponse>> update(@PathVariable String key,
            @Valid @RequestBody ParameterUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(parameterService.update(key, request)));
    }

    @GetMapping("/{key}/history")
    @Operation(summary = "Read parameter history, newest version first (ADMIN only)",
            description = "Additive UC-46 contract. Page starts at 0; size is between 1 and 100")
    public ResponseEntity<ApiResponse<PageResponse<ParameterHistoryResponse>>> history(@PathVariable String key,
            @Valid @ModelAttribute @ParameterObject ParameterHistoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(parameterService.history(key, request.getPage(), request.getSize())));
    }
}
