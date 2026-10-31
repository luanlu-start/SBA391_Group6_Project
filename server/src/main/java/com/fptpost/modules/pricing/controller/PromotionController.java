package com.fptpost.modules.pricing.controller;

import com.fptpost.common.response.*;
import com.fptpost.modules.pricing.dto.request.*;
import com.fptpost.modules.pricing.dto.response.*;
import com.fptpost.modules.pricing.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/admin/promotions") @RequiredArgsConstructor
@Tag(name = "Promotions", description = "UC-45 administration. UC-44 application is a separate owner workflow.")
public class PromotionController {
    private final PromotionService promotions;

    @GetMapping @Operation(summary = "List promotions with actual usage counts (ADMIN only)")
    public ResponseEntity<ApiResponse<PageResponse<PromotionResponse>>> list(
            @Valid @ModelAttribute @ParameterObject PromotionSearchRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(promotions.list(request)));
    }

    @GetMapping("/target-options") @Operation(summary = "Find target users by name/email/UUID (ADMIN only, supplementary)")
    public ResponseEntity<ApiResponse<PromotionTargetOptionsResponse>> targetOptions(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(promotions.targetOptions(q, limit)));
    }

    @GetMapping("/{id}") @Operation(summary = "Read a promotion (ADMIN only, supplementary)")
    public ResponseEntity<ApiResponse<PromotionResponse>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(promotions.detail(id)));
    }

    @PostMapping @Operation(summary = "Create a promotion (ADMIN only)")
    public ResponseEntity<ApiResponse<PromotionResponse>> create(@Valid @RequestBody PromotionWriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.created("Promotion created", promotions.create(request)));
    }

    @PutMapping("/{id}") @Operation(summary = "Edit/pause a promotion with its expected version (ADMIN only)")
    public ResponseEntity<ApiResponse<PromotionResponse>> update(@PathVariable UUID id,
            @Valid @RequestBody PromotionWriteRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(promotions.update(id, request)));
    }
}
