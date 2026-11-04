package com.fptpost.common.audit;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties("audit.outbox")
public class AuditOutboxProperties {
    private boolean enabled = true;
    @Min(100) private long fixedDelayMs = 2000;
    @Min(1) @Max(100) private int batchSize = 25;
    @Min(1024) @Max(1048576) private int maxPayloadBytes = 262144;
    @NotNull private Duration leaseDuration = Duration.ofSeconds(30);
    @NotNull private Duration retryBaseDelay = Duration.ofSeconds(2);
    @NotNull private Duration retryMaxDelay = Duration.ofMinutes(5);

    @AssertTrue(message = "Outbox lease/retry durations must be positive and retry max >= retry base")
    public boolean isDurationsValid() {
        return leaseDuration != null && retryBaseDelay != null && retryMaxDelay != null
                && leaseDuration.compareTo(Duration.ZERO) > 0
                && retryBaseDelay.compareTo(Duration.ofMillis(1)) >= 0
                && retryMaxDelay.compareTo(retryBaseDelay) >= 0;
    }
}
