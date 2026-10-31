package com.fptpost.common.mail.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {
    @NotBlank
    @Email
    private String from = "no-reply@fptpost.local";

    @NotBlank
    @Pattern(regexp = "[^\\r\\n]+")
    private String fromName = "FPTPost";

    private boolean templateCache = false;
}
