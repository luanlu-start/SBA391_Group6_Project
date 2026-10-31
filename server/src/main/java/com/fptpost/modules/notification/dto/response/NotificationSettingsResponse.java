package com.fptpost.modules.notification.dto.response;

import java.util.List;

public record NotificationSettingsResponse(List<NotificationSettingResponse> settings, boolean smsAvailable,
        boolean webPushAvailable) { }
