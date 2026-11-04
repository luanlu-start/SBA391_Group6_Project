package com.fptpost.modules.identity.config;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.registration")
public class RegistrationProperties {
    @Min(8)
    @Max(72)
    private int passwordMinLength = 8;
    @Min(1)
    @Max(30)
    private int otpTtlMinutes = 5;
    @Min(1)
    @Max(10)
    private int otpMaxAttempts = 5;
    @Min(1)
    @Max(3600)
    private int otpResendCooldownSeconds = 60;
    @Min(1)
    @Max(1000)
    private int requestsPerMinute = 10;
    @NotBlank
    @Size(max = 50)
    private String termsVersion = "draft-2026-10-09";
    @NotBlank
    @Size(max = 50)
    private String privacyVersion = "draft-2026-10-09";
}
