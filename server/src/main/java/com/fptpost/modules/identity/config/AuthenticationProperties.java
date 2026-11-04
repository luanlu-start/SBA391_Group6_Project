package com.fptpost.modules.identity.config;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.authentication")
public class AuthenticationProperties {
    private String jwtSecret = "";
    @NotBlank
    private String issuer = "fptpost";
    @Min(1)
    @Max(60)
    private int accessTtlMinutes = 15;
    @Min(1)
    @Max(30)
    private int refreshTtlDays = 7;
    @Min(0)
    @Max(365)
    private int expiredSessionRetentionDays = 7;
    @Min(1)
    @Max(10)
    private int maxLoginAttempts = 5;
    @Min(1)
    @Max(60)
    private int loginLockMinutes = 15;
    @Min(1)
    @Max(20)
    private int maxDevices = 5;
    private boolean cookieSecure;
}
