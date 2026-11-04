package com.fptpost.modules.catalog.config;

import com.fptpost.modules.catalog.service.PricingSimulationPort;
import com.fptpost.modules.catalog.service.ReferencePricingSimulation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
public class PricingSimulationConfig {
    @Bean
    @Profile("!prod & !staging & (dev | local | demo | test)")
    @ConditionalOnMissingBean(PricingSimulationPort.class)
    @ConditionalOnProperty(prefix = "app.parameters.simulation", name = "reference-enabled", havingValue = "true", matchIfMissing = true)
    PricingSimulationPort referencePricingSimulation() { return new ReferencePricingSimulation(); }
}
