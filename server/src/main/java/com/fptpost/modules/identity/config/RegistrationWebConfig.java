package com.fptpost.modules.identity.config;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.service.RegistrationRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration @RequiredArgsConstructor
public class RegistrationWebConfig implements WebMvcConfigurer {
    private final RegistrationRateLimiter limiter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if ("POST".equals(request.getMethod())) {
                    try {
                        // Count malformed requests too; do not trust client-supplied forwarded headers.
                        limiter.check(request.getRemoteAddr());
                    } catch (AppException exception) {
                        response.setHeader("Retry-After", "60");
                        throw exception;
                    }
                }
                return true;
            }
        }).addPathPatterns("/api/v1/auth/register");
    }
}
