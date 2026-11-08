package com.fptpost.modules.guarantee.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.guarantee.dto.request.CompensationDecisionRequest;
import com.fptpost.modules.guarantee.dto.request.FundContributionRequest;
import com.fptpost.modules.guarantee.dto.response.CompensationResponse;
import com.fptpost.modules.guarantee.dto.response.GuaranteeFundResponse;
import com.fptpost.modules.guarantee.service.GuaranteeFundService;
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
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Guarantee fund", description = "UC-52 Quỹ bảo đảm và bồi thường theo phán quyết")
public class GuaranteeFundController {

    private final GuaranteeFundService guaranteeFundService;

    @GetMapping("/guarantee-fund")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Số dư, khoản chờ duyệt, hàng đợi và biến động Quỹ bảo đảm (API-129)")
    public ResponseEntity<ApiResponse<GuaranteeFundResponse>> overview() {
        return json(guaranteeFundService.overview());
    }

    @GetMapping("/guarantee-fund/compensations")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Danh sách bồi thường theo trạng thái, xếp theo thứ tự phán quyết")
    public ResponseEntity<ApiResponse<PageResponse<CompensationResponse>>> compensations(
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return json(guaranteeFundService.list(status, page, size));
    }

    @PostMapping("/guarantee-fund/contributions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ghi nhận khoản trích vào Quỹ bảo đảm, sau đó chi các khoản đang xếp hàng")
    public ResponseEntity<ApiResponse<GuaranteeFundResponse>> contribute(@Valid @RequestBody FundContributionRequest request) {
        return json(guaranteeFundService.contribute(request));
    }

    @PostMapping("/complaints/{id}/compensation")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Duyệt và chi, hoặc từ chối bồi thường của khiếu nại (API-128)")
    public ResponseEntity<ApiResponse<CompensationResponse>> decide(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                                    @Valid @RequestBody CompensationDecisionRequest request) {
        return json(guaranteeFundService.decide(UUID.fromString(jwt.getSubject()), id, request));
    }

    private <T> ResponseEntity<ApiResponse<T>> json(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }
}
