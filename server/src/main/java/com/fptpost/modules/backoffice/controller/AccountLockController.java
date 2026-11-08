package com.fptpost.modules.backoffice.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.backoffice.dto.request.AccountLockRequest;
import com.fptpost.modules.backoffice.dto.request.AccountUnlockRequest;
import com.fptpost.modules.backoffice.dto.request.BlacklistRequest;
import com.fptpost.modules.backoffice.dto.response.AdminUserDetailResponse;
import com.fptpost.modules.backoffice.service.AccountLockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Account locks", description = "UC-56 khóa, mở khóa tài khoản và danh sách đen")
public class AccountLockController {

    private final AccountLockService accountLockService;

    @PostMapping("/users/{publicId}/lock")
    @Operation(summary = "Khóa tạm thời hoặc vĩnh viễn, thu hồi mọi phiên (API-141)")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> lock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID publicId,
                                                                     @Valid @RequestBody AccountLockRequest request) {
        return json(accountLockService.lock(actor(jwt), publicId, request));
    }

    @PostMapping("/users/{publicId}/unlock")
    @Operation(summary = "Mở khóa kèm lý do (API-142)")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> unlock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID publicId,
                                                                       @Valid @RequestBody AccountUnlockRequest request) {
        return json(accountLockService.unlock(actor(jwt), publicId, request));
    }

    @PostMapping("/blacklist")
    @Operation(summary = "Thêm SĐT, CCCD hoặc thiết bị vào danh sách đen; chỉ lưu giá trị băm (API-143)")
    public ResponseEntity<Void> blacklist(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BlacklistRequest request) {
        accountLockService.addToBlacklist(actor(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private <T> ResponseEntity<ApiResponse<T>> json(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
