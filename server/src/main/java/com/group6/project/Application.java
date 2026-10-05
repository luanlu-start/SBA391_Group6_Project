package com.group6.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        var app = new SpringApplication(Application.class);
        Environment env = app.run(args).getEnvironment();
        
        String protocol = "http";
        String serverPort = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        
        log.info("""
            -----------------------------------------------------------------------
            \tApplication '{}' is running!
            \tLocal Access URL:      {}://localhost:{}{}
            \tSwagger Documentation: {}://localhost:{}{}/swagger-ui/index.html
            \tHealth Check URL:      {}://localhost:{}{}/api/v1/health
            -----------------------------------------------------------------------
            """,
            env.getProperty("spring.application.name"),
            protocol, serverPort, contextPath,
            protocol, serverPort, contextPath,
            protocol, serverPort, contextPath
        );
    }
}
