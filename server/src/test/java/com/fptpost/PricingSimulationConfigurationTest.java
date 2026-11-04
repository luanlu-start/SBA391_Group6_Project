package com.fptpost;

import com.fptpost.modules.catalog.config.PricingSimulationConfig;
import com.fptpost.modules.catalog.service.PricingSimulationPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class PricingSimulationConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PricingSimulationConfig.class);

    @Test void developmentProvidesReferenceButProductionCannotActivateItEvenAlongsideDev() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context ->
                assertThat(context).hasSingleBean(PricingSimulationPort.class));
        for (String profiles : new String[]{"prod", "staging", "dev,prod", "demo,staging"}) {
            runner.withPropertyValues("spring.profiles.active=" + profiles).run(context ->
                    assertThat(context).doesNotHaveBean(PricingSimulationPort.class));
        }
    }

    @Test void explicitDisableDoesNotCreateAReferenceAdapter() {
        runner.withPropertyValues("spring.profiles.active=dev", "app.parameters.simulation.reference-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PricingSimulationPort.class));
    }

    @Test void existingRealPricingPortIsUsedWithoutAReferenceFallback() {
        PricingSimulationPort real = (quote, snapshot) -> { throw new IllegalStateException("upstream failure"); };
        runner.withPropertyValues("spring.profiles.active=dev").withBean(PricingSimulationPort.class, () -> real)
                .run(context -> assertThat(context.getBean(PricingSimulationPort.class)).isSameAs(real));
    }
}
