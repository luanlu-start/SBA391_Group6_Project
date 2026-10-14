package com.fptpost.modules.catalog.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.catalog.dto.request.PricingSimulationRequest;
import com.fptpost.modules.catalog.dto.response.PricingSimulationResponse;
import com.fptpost.modules.catalog.service.ParameterSimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/admin/params")
@Tag(name = "System parameters")
public class ParameterSimulationController {
    private final ParameterSimulationService simulation;

    @PostMapping("/simulate")
    @Operation(summary = "Preview proposed parameters without saving", description = "Reference adapter is restricted to development profiles; production requires a real PricingSimulationPort.")
    public ResponseEntity<ApiResponse<PricingSimulationResponse>> simulate(@Valid @RequestBody PricingSimulationRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ApiResponse.success(simulation.simulate(request)));
    }
}
