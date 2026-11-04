package com.fptpost.modules.catalog.service;

import com.fptpost.modules.catalog.dto.internal.ParameterSnapshot;
import com.fptpost.modules.catalog.dto.request.PricingQuoteRequest;
import com.fptpost.modules.catalog.dto.response.PricingSimulationResponse;

/** Narrow handoff to the Pricing module; never writes an order, parameter or ledger entry. */
public interface PricingSimulationPort {
    PricingSimulationResponse simulate(PricingQuoteRequest quote, ParameterSnapshot snapshot);
}
