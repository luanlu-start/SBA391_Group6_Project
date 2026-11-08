package com.fptpost.modules.moderation.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.moderation.dto.request.OrderModerationRejectRequest;
import com.fptpost.modules.moderation.dto.request.OrderModerationSearchRequest;
import com.fptpost.modules.moderation.dto.response.OrderModerationOutcomeResponse;
import com.fptpost.modules.moderation.dto.response.OrderModerationResponse;
import com.fptpost.modules.moderation.service.OrderModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
@Tag(name = "Order moderation", description = "UC-54 kiểm duyệt đơn hàng")
public class OrderModerationController {

    private final OrderModerationService orderModerationService;

    @GetMapping("/mod/orders/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Hàng đợi kiểm duyệt đơn, đơn gửi trước hiển thị trước (API-136)")
    public ResponseEntity<ApiResponse<PageResponse<OrderModerationResponse>>> pending(
            @AuthenticationPrincipal Jwt jwt, @Valid @ModelAttribute @ParameterObject OrderModerationSearchRequest request) {
        return json(orderModerationService.pending(actor(jwt), request));
    }

    @GetMapping("/mod/orders/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Chi tiết kiểm duyệt một đơn")
    public ResponseEntity<ApiResponse<OrderModerationResponse>> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
        return json(orderModerationService.detail(actor(jwt), orderId));
    }

    @PostMapping("/mod/orders/{orderId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Duyệt đơn, đưa đơn lên sàn (API-137)")
    public ResponseEntity<ApiResponse<OrderModerationResponse>> approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
        return json(orderModerationService.approve(actor(jwt), orderId));
    }

    @PostMapping("/mod/orders/{orderId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Từ chối đơn kèm lý do gửi người gửi (API-138)")
    public ResponseEntity<ApiResponse<OrderModerationResponse>> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId,
                                                                       @Valid @RequestBody OrderModerationRejectRequest request) {
        return json(orderModerationService.reject(actor(jwt), orderId, request));
    }

    @GetMapping("/orders/{orderId}/moderation")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kết quả kiểm duyệt đơn của tôi")
    public ResponseEntity<ApiResponse<OrderModerationOutcomeResponse>> outcome(@AuthenticationPrincipal Jwt jwt,
                                                                               @PathVariable Long orderId) {
        return json(orderModerationService.outcome(actor(jwt), orderId));
    }

    private <T> ResponseEntity<ApiResponse<T>> json(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
