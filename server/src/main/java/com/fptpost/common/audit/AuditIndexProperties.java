package com.fptpost.common.audit;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("audit.indexes")
public class AuditIndexProperties {
    private boolean enabled = true;
    @Min(1000)
    private long fixedDelayMs = 60000;
}
