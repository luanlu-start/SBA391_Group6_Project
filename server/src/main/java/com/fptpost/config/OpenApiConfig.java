package com.fptpost.config;

import io.swagger.v3.oas.models.OpenAPI;
import com.fptpost.common.response.ApiProblem;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Set;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenApiCustomizer problemDetailsContract() {
        return api -> {
            if (api.getComponents() == null) api.setComponents(new Components());
            ModelConverters.getInstance().read(ApiProblem.class).forEach(api.getComponents()::addSchemas);
            api.getPaths().forEach((path, item) -> {
                if (!path.startsWith("/api/v1/")) return;
                item.readOperations().forEach(operation -> {
                    boolean publicEndpoint = Set.of("/api/v1/health", "/api/v1/auth/csrf", "/api/v1/auth/registration-policies",
                            "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/otp/verify",
                            "/api/v1/auth/otp/resend").contains(path);
                    if (!publicEndpoint) {
                        operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                        operation.getResponses().putIfAbsent("401", new ApiResponse().description("Cần đăng nhập"));
                        operation.getResponses().putIfAbsent("403", new ApiResponse().description("Không có quyền truy cập"));
                    }
                    if (Set.of("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").contains(path)) {
                        operation.addParametersItem(new Parameter().in("header").name("X-XSRF-TOKEN").required(true)
                                .description("Token từ GET /api/v1/auth/csrf; gửi cùng cookie XSRF-TOKEN")
                                .schema(new StringSchema()));
                        for (String status : new String[]{"401", "403", "429"})
                            operation.getResponses().putIfAbsent(status, new ApiResponse().description("Lỗi xác thực/phiên"));
                    }
                    operation.addParametersItem(new Parameter().in("header").name("X-Correlation-Id")
                            .description("UUID để theo dõi request; backend sinh mới nếu thiếu hoặc không hợp lệ")
                            .schema(new StringSchema().format("uuid")));
                    operation.getResponses().putIfAbsent("400", new ApiResponse().description("Yêu cầu không hợp lệ"));
                    operation.getResponses().putIfAbsent("500", new ApiResponse().description("Lỗi hệ thống"));
                    if (path.contains("{id}")) operation.getResponses().putIfAbsent("404", new ApiResponse().description("Không tìm thấy tài nguyên"));
                    if (path.equals("/api/v1/auth/register")) {
                        for (String status : new String[]{"403", "409", "429", "503"}) {
                            operation.getResponses().putIfAbsent(status, new ApiResponse().description("Lỗi đăng ký"));
                        }
                    }
                    operation.getResponses().forEach((status, response) -> {
                        response.addHeaderObject("X-Correlation-Id", new Header().schema(new StringSchema().format("uuid")));
                        if (status.matches("[45][0-9X]{2}")) {
                            response.setContent(new Content().addMediaType("application/problem+json",
                                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiProblem"))));
                        }
                        if (status.equals("429")) response.addHeaderObject("Retry-After", new Header().schema(new StringSchema()).example("60"));
                    });
                });
            });
        };
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
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
