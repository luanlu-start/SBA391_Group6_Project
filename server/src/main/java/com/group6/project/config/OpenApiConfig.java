package com.group6.project.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SBA391 Group 6 RESTful API")
                        .version("1.0.0")
                        .description("Enterprise RESTful API Documentation for SBA391 / SBA301 Project. Provides endpoints for Products, Auth, and System Health.")
                        .contact(new Contact()
                                .name("Group 6 Engineering Team")
                                .email("team6@sba391.edu.vn"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://springdoc.org")));
    }
}
