package com.fptpost.modules.notification.dto.response;

import com.fptpost.modules.notification.enums.*;
import java.util.Map;

public record NotificationSettingResponse(NotificationCategory category, boolean mandatory,
        Map<NotificationChannel, Boolean> channels) { }
