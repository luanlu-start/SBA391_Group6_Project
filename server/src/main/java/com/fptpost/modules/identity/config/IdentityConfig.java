package com.fptpost.modules.identity.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({RegistrationProperties.class, AuthenticationProperties.class})
public class IdentityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public Clock identityClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecureRandom identityRandom() {
        return new SecureRandom();
    }
}
