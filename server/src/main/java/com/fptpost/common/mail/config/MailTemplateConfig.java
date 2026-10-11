package com.fptpost.common.mail.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailTemplateConfig {
    @Bean
    public TemplateEngine emailTemplateEngine(MailProperties properties) {
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addTemplateResolver(resolver("html/*", ".html", TemplateMode.HTML, properties));
        templateEngine.addTemplateResolver(resolver("text/*", ".txt", TemplateMode.TEXT, properties));
        templateEngine.addTemplateResolver(resolver("subject/*", ".txt", TemplateMode.TEXT, properties));
        return templateEngine;
    }

    private ClassLoaderTemplateResolver resolver(String pattern, String suffix, TemplateMode mode,
                                                 MailProperties properties) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/mail/");
        resolver.setSuffix(suffix);
        resolver.setResolvablePatterns(Set.of(pattern));
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(properties.isTemplateCache());
        return resolver;
    }
}
