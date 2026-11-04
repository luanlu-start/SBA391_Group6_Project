package com.fptpost.modules.catalog.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.catalog.dto.request.PricingSimulationRequest;
import com.fptpost.modules.catalog.dto.response.PricingSimulationResponse;
import com.fptpost.modules.catalog.exception.PricingSimulationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class ParameterSimulationService {
    private final ParameterService parameterService;
    private final ObjectProvider<PricingSimulationPort> pricing;

    @PreAuthorize("hasRole('ADMIN')")
    public PricingSimulationResponse simulate(PricingSimulationRequest request) {
        PricingSimulationPort adapter = pricing.getIfAvailable();
        if (adapter == null) throw new AppException(PricingSimulationErrorCode.UNAVAILABLE);
        return adapter.simulate(request.getQuote(), parameterService.proposedSnapshot(request.getOverrides()));
    }
}
