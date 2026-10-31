package com.fptpost.modules.notification.controller;

import com.fptpost.common.exception.*;
import com.fptpost.common.response.*;
import com.fptpost.modules.notification.dto.response.NotificationResponse;
import com.fptpost.modules.notification.service.NotificationInboxStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/notifications") @RequiredArgsConstructor @PreAuthorize("isAuthenticated()")
@Tag(name = "Notifications", description = "Private UC-53 inbox")
public class NotificationController {
    private final NotificationInboxStore inbox;

    @GetMapping @Operation(summary = "List current user's notifications")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean unreadOnly, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) throw new AppException(GlobalErrorCode.INVALID_REQUEST);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(inbox.list(owner(jwt), unreadOnly, page, size)));
    }

    @GetMapping("/unread-count") @Operation(summary = "Count current user's unread notifications")
    public ResponseEntity<ApiResponse<UnreadCount>> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(new UnreadCount(inbox.unreadCount(owner(jwt)))));
    }

    @PostMapping("/{id}/read") @Operation(summary = "Mark an owned notification read")
    public ResponseEntity<ApiResponse<NotificationResponse>> read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(inbox.read(owner(jwt), id)));
    }

    @PostMapping("/read-all") @Operation(summary = "Mark current user's inbox read")
    public ResponseEntity<ApiResponse<ReadAllResult>> readAll(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(new ReadAllResult(inbox.readAll(owner(jwt)))));
    }

    private UUID owner(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    public record UnreadCount(long count) { }
    public record ReadAllResult(long updatedCount) { }
}
