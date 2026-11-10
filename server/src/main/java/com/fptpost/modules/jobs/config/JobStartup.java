package com.fptpost.modules.jobs.config;

import com.fptpost.common.persistence.EnumCheckConstraints;
import com.fptpost.modules.jobs.service.JobRunner;
import com.fptpost.modules.order.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobStartup implements ApplicationRunner {

    private final JobRunner jobRunner;
    private final EnumCheckConstraints enumCheckConstraints;

    @Override
    public void run(ApplicationArguments arguments) {
        enumCheckConstraints.synchronize("orders", "status", OrderStatus.class);
        jobRunner.ensureLockRows();
    }
}
