package com.fptpost.modules.identity.config;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.*;

@Getter @Setter @Component @ConfigurationProperties(prefix = "app.profile")
public class ProfileProperties {
    private List<String> fptEmailDomains = new ArrayList<>(List.of("fpt.edu.vn", "fe.edu.vn"));
    public boolean isFptEmail(String email) {
        String domain = email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        return fptEmailDomains.stream().map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(value -> !value.isBlank() && (domain.equals(value) || domain.endsWith("." + value)));
    }
}
