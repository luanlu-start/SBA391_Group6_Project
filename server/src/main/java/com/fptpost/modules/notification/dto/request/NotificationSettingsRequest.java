package com.fptpost.modules.notification.dto.request;

import com.fptpost.modules.notification.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public record NotificationSettingsRequest(@NotNull @Size(min = 1, max = 7) List<@NotNull @Valid Setting> settings) {
    public record Setting(@NotNull NotificationCategory category,
            @NotNull @Size(min = 3, max = 3) Map<@NotNull NotificationChannel, @NotNull Boolean> channels) { }
}
