package com.fptpost.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.exception.ProblemResponseFactory;
import com.fptpost.common.request.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter(ProblemResponseFactory problems, ObjectMapper mapper) {
        CorsConfiguration config = new CorsConfiguration();
        
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Origin", "Content-Type", "Accept", "Authorization", "X-Requested-With", "X-Correlation-Id", "X-XSRF-TOKEN"));
        config.setExposedHeaders(Arrays.asList("Authorization", "Link", "X-Total-Count", "X-Correlation-Id", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        CorsFilter filter = new CorsFilter(source);
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override
            protected void rejectRequest(ServerHttpResponse response) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
            }

            @Override
            public boolean processRequest(CorsConfiguration configuration, HttpServletRequest request,
                                          HttpServletResponse response) throws IOException {
                boolean allowed = super.processRequest(configuration, request, response);
                if (!allowed) {
                    var problem = problems.create(GlobalErrorCode.FORBIDDEN, 403, request, List.of());
                    response.setContentType("application/problem+json");
                    response.setHeader("Cache-Control", "no-store");
                    response.setHeader(CorrelationIdFilter.HEADER, problem.getCorrelationId());
                    mapper.writeValue(response.getOutputStream(), problem);
                }
                return allowed;
            }
        });
        return filter;
    }
}
