package com.fptpost.modules.moderation.config;

import com.fptpost.common.persistence.EnumCheckConstraints;
import com.fptpost.modules.order.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ModerationSchemaConstraints implements ApplicationRunner {

    private final EnumCheckConstraints enumCheckConstraints;

    @Override
    public void run(ApplicationArguments arguments) {
        enumCheckConstraints.synchronize("orders", "status", OrderStatus.class);
    }
}
