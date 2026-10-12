package com.fptpost.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.exception.*;
import com.fptpost.modules.identity.config.AuthenticationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.CorsFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean FilterRegistrationBean<CorsFilter> corsRegistration(CorsFilter filter) {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, CorsFilter corsFilter,
            ProblemResponseFactory problems, ObjectMapper mapper, AuthenticationProperties properties) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Strict").secure(properties.isCookieSecure()));
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        http.addFilterBefore(corsFilter, CsrfFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable()).formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(request -> "POST".equals(request.getMethod()) && Set.of(
                                "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")
                                .contains(request.getRequestURI().substring(request.getContextPath().length()))))
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/actuator/health/**", "/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html", "/api/v1/auth/registration-policies", "/api/v1/auth/csrf").permitAll()
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/otp/verify", "/api/v1/auth/otp/resend",
                                "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN").anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) ->
                                writeProblem(problems, mapper, request, response, GlobalErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(problems, mapper, request, response, GlobalErrorCode.FORBIDDEN)))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, exception) ->
                                writeProblem(problems, mapper, request, response, GlobalErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(problems, mapper, request, response, GlobalErrorCode.FORBIDDEN)));
        return http.build();
    }
    private static void writeProblem(ProblemResponseFactory problems, ObjectMapper mapper, HttpServletRequest request,
            HttpServletResponse response, ErrorCode error) throws IOException {
        response.setStatus(error.getHttpStatus().value());
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), problems.create(error, error.getHttpStatus().value(), request, List.of()));
    }
}
