package com.group6.project.modules.health;

import com.group6.project.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health Check", description = "System Health and Connectivity Check API")
public class HealthController {

    @Value("${spring.application.name:sba391-server}")
    private String appName;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @GetMapping
    @Operation(summary = "Check backend server status", description = "Returns system uptime, environment, and timestamp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth() {
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("status", "UP");
        healthInfo.put("service", appName);
        healthInfo.put("profile", activeProfile);
        healthInfo.put("timestamp", Instant.now().toString());
        healthInfo.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        healthInfo.put("jvmVersion", System.getProperty("java.version"));

        return ResponseEntity.ok(ApiResponse.success("Backend service is healthy and operational", healthInfo));
    }
}
