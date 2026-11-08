package com.fptpost.modules.backoffice.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.backoffice.dto.request.AccountRequestRejectRequest;
import com.fptpost.modules.backoffice.dto.request.AccountRequestSearchRequest;
import com.fptpost.modules.backoffice.dto.response.AccountRequestResponse;
import com.fptpost.modules.backoffice.service.AccountRequestAdministrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Account data requests", description = "UC-57 xử lý yêu cầu xuất dữ liệu và xóa tài khoản")
public class AccountRequestController {

    private final AccountRequestAdministrationService accountRequestAdministrationService;

    @GetMapping("/admin/account-requests")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Yêu cầu xuất/xóa dữ liệu; mặc định đang chờ, cũ nhất trước")
    public ResponseEntity<ApiResponse<PageResponse<AccountRequestResponse>>> list(
            @Valid @ModelAttribute @ParameterObject AccountRequestSearchRequest request) {
        return json(accountRequestAdministrationService.list(request));
    }

    @PostMapping("/admin/account-requests/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Hoàn tất: mở bản sao dữ liệu cho người dùng hoặc ẩn danh tài khoản")
    public ResponseEntity<ApiResponse<AccountRequestResponse>> complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return json(accountRequestAdministrationService.complete(actor(jwt), id));
    }

    @PostMapping("/admin/account-requests/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Từ chối kèm lý do")
    public ResponseEntity<ApiResponse<AccountRequestResponse>> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                                      @Valid @RequestBody AccountRequestRejectRequest request) {
        return json(accountRequestAdministrationService.reject(actor(jwt), id, request));
    }

    @GetMapping("/admin/account-requests/{id}/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xem trước bản sao dữ liệu sẽ cung cấp cho người dùng")
    public ResponseEntity<byte[]> preview(@PathVariable UUID id) {
        return download(accountRequestAdministrationService.previewExport(id));
    }

    @GetMapping("/me/data-requests/{id}/export")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Người dùng tải bản sao dữ liệu đã được duyệt (trong 7 ngày)")
    public ResponseEntity<byte[]> ownExport(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return download(accountRequestAdministrationService.ownExport(actor(jwt), id));
    }

    private ResponseEntity<byte[]> download(byte[] content) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("fptpost-du-lieu-ca-nhan.json").build().toString())
                .body(content);
    }

    private <T> ResponseEntity<ApiResponse<T>> json(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
