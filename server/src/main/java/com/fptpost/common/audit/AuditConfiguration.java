package com.fptpost.common.audit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AuditOutboxProperties.class, AuditIndexProperties.class})
public class AuditConfiguration {
}
