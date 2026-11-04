package com.fptpost.modules.identity.config;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;

@Getter @Setter @Component @Validated @ConfigurationProperties(prefix = "app.kyc")
public class KycProperties {
    @NotBlank @Size(max = 50) private String policyVersion = "kyc-v1";
}
