package com.fptpost.modules.notification.service;

import com.fptpost.modules.notification.enums.NotificationChannel;
import com.fptpost.modules.notification.model.NotificationEvent;
import java.util.UUID;

public record NotificationDeliveryClaim(UUID id, UUID token, NotificationChannel channel, int attempt,
        NotificationEvent event) { }
