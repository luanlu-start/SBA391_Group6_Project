package com.fptpost.modules.notification.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.notification.dto.request.NotificationSettingsRequest;
import com.fptpost.modules.notification.dto.response.NotificationSettingsResponse;
import com.fptpost.modules.notification.service.NotificationSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/me/notification-settings") @RequiredArgsConstructor
@Tag(name = "Notification settings")
public class NotificationSettingsController {
    private final NotificationSettingsService settings;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(settings.get(UUID.fromString(jwt.getSubject()))));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> update(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NotificationSettingsRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(settings.update(UUID.fromString(jwt.getSubject()), request)));
    }
}
