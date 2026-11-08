package com.fptpost.modules.backoffice.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.backoffice.dto.request.AdminUserRolesRequest;
import com.fptpost.modules.backoffice.dto.request.AdminUserSearchRequest;
import com.fptpost.modules.backoffice.dto.request.StaffCreationRequest;
import com.fptpost.modules.backoffice.dto.response.AdminUserDetailResponse;
import com.fptpost.modules.backoffice.dto.response.AdminUserSummaryResponse;
import com.fptpost.modules.backoffice.service.UserAdministrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
@Tag(name = "Admin users", description = "UC-57 quản lý người dùng và nhân sự")
public class AdminUserController {

    private final UserAdministrationService userAdministrationService;

    @GetMapping("/users")
    @Operation(summary = "Tìm kiếm người dùng theo email, SĐT, tên hoặc mã (API-144)")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserSummaryResponse>>> search(
            @Valid @ModelAttribute @ParameterObject AdminUserSearchRequest request) {
        return json(userAdministrationService.search(request));
    }

    @GetMapping("/users/{publicId}")
    @Operation(summary = "Chi tiết người dùng: hồ sơ, đơn, giao dịch, yêu cầu dữ liệu (API-145)")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID publicId) {
        return json(userAdministrationService.detail(actor(jwt), publicId));
    }

    @PutMapping("/users/{publicId}/roles")
    @Operation(summary = "Gán vai trò; phiên hiện tại của người dùng bị thu hồi (API-146)")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> roles(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID publicId,
                                                                      @Valid @RequestBody AdminUserRolesRequest request) {
        return json(userAdministrationService.updateRoles(actor(jwt), publicId, request));
    }

    @GetMapping("/staff")
    @Operation(summary = "Danh sách tài khoản nhân sự (Admin, Kiểm duyệt)")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserSummaryResponse>>> staff(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return json(userAdministrationService.staff(page, size));
    }

    @PostMapping("/staff")
    @Operation(summary = "Tạo tài khoản nhân sự; nhân sự tự đặt mật khẩu qua Quên mật khẩu (API-147)")
    public ResponseEntity<ApiResponse<AdminUserSummaryResponse>> createStaff(@Valid @RequestBody StaffCreationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.created("Đã tạo tài khoản nhân sự", userAdministrationService.createStaff(request)));
    }

    private <T> ResponseEntity<ApiResponse<T>> json(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
