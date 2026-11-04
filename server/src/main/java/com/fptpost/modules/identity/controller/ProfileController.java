package com.fptpost.modules.identity.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.dto.request.*;
import com.fptpost.modules.identity.dto.response.*;
import com.fptpost.modules.identity.enums.AccountRequestType;
import com.fptpost.modules.identity.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/v1/me") @RequiredArgsConstructor
public class ProfileController {
    private final ProfileService profileService;
    @PutMapping("/profile") @Operation(summary = "UC-07: Cập nhật hồ sơ cá nhân")
    public ResponseEntity<ApiResponse<UserResponse>> update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdateRequest request) {
        return ok(profileService.update(owner(jwt), session(jwt), request));
    }
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @Operation(summary = "UC-07: Thay ảnh đại diện")
    public ResponseEntity<ApiResponse<UserResponse>> avatar(@AuthenticationPrincipal Jwt jwt, @RequestParam MultipartFile file) {
        return ok(profileService.updateAvatar(owner(jwt), session(jwt), file));
    }
    @GetMapping("/avatar") @Operation(summary = "UC-07: Xem ảnh đại diện của tôi")
    public ResponseEntity<byte[]> avatar(@AuthenticationPrincipal Jwt jwt) {
        var image = profileService.avatar(owner(jwt));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(image.mediaType()))
                .header("X-Content-Type-Options", "nosniff").body(image.bytes());
    }
    @GetMapping("/addresses") @Operation(summary = "UC-07: Danh sách địa chỉ của tôi")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> addresses(@AuthenticationPrincipal Jwt jwt) { return ok(profileService.addresses(owner(jwt))); }
    @PostMapping("/addresses") @Operation(summary = "UC-07: Thêm địa chỉ")
    public ResponseEntity<ApiResponse<AddressResponse>> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(ApiResponse.created("Đã lưu địa chỉ.", profileService.saveAddress(owner(jwt), session(jwt), null, request)));
    }
    @PutMapping("/addresses/{id}") @Operation(summary = "UC-07: Sửa địa chỉ thuộc tài khoản")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody AddressRequest request) {
        return ok(profileService.saveAddress(owner(jwt), session(jwt), id, request));
    }
    @DeleteMapping("/addresses/{id}") @Operation(summary = "UC-07: Xóa địa chỉ thuộc tài khoản")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        profileService.deleteAddress(owner(jwt), session(jwt), id); return ok(null);
    }
    @GetMapping("/data-requests") @Operation(summary = "UC-07: Xem các yêu cầu dữ liệu cá nhân của tôi")
    public ResponseEntity<ApiResponse<List<AccountDataRequestResponse>>> requests(@AuthenticationPrincipal Jwt jwt) { return ok(profileService.dataRequests(owner(jwt))); }
    @PostMapping("/data-export") @Operation(summary = "UC-07: Tạo yêu cầu xuất dữ liệu để Admin xử lý")
    public ResponseEntity<ApiResponse<AccountDataRequestResponse>> export(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AccountDataRequestInput request) {
        return ok(profileService.requestData(owner(jwt), session(jwt), AccountRequestType.DATA_EXPORT, request.getReason()));
    }
    @PostMapping("/delete-request") @Operation(summary = "UC-07: Tạo yêu cầu xóa/ẩn danh để Admin xử lý")
    public ResponseEntity<ApiResponse<AccountDataRequestResponse>> deleteRequest(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AccountDataRequestInput request) {
        return ok(profileService.requestData(owner(jwt), session(jwt), AccountRequestType.ACCOUNT_DELETION, request.getReason()));
    }
    private UUID owner(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private UUID session(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("sid")); }
    private <T> ResponseEntity<ApiResponse<T>> ok(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data)); }
}
