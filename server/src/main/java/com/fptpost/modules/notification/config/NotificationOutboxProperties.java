package com.fptpost.modules.notification.config;

import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter @Setter @Component @Validated @ConfigurationProperties(prefix = "notification.outbox")
public class NotificationOutboxProperties {
    private boolean enabled = true;
    @Min(1) @Max(100) private int batchSize = 25;
    @Min(30) @Max(900) private int leaseSeconds = 120;
    @Min(1) @Max(60) private int retryBaseSeconds = 5;
    @Min(1) @Max(3600) private int retryMaxSeconds = 300;
}
