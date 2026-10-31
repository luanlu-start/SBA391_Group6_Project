package com.fptpost.modules.notification.dto.response;

import com.fptpost.modules.notification.enums.NotificationCategory;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, NotificationCategory category, String title, String body,
        String relativeLink, Instant at, Instant readAt) { }
