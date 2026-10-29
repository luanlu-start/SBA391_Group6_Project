package com.fptpost.modules.audit.retention;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("audit.retention")
public class AuditRetentionProperties {
    private boolean enabled = true;
    @Min(1000)
    private long initialDelayMs = 60000;
    @Min(1000)
    private long fixedDelayMs = 3600000;
    @Min(1)
    @Max(1000)
    private int batchMax = 250;
}
