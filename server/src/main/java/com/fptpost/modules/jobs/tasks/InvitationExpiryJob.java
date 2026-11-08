package com.fptpost.modules.jobs.tasks;

import com.fptpost.modules.jobs.repository.OrderMaintenanceRepository;
import com.fptpost.modules.jobs.service.BackgroundJob;
import com.fptpost.modules.jobs.service.JobResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class InvitationExpiryJob implements BackgroundJob {

    private final OrderMaintenanceRepository orders;

    @Override
    public String name() {
        return "invitation-expiry";
    }

    @Override
    public String title() {
        return "Hết hạn lời mời người giao";
    }

    @Override
    public String description() {
        return "Chuyển lời mời trực tiếp chưa được phản hồi quá hạn sang Hết hạn.";
    }

    @Override
    public Duration interval() {
        return Duration.ofMinutes(15);
    }

    @Override
    public JobResult execute(Instant now) {
        int expired = orders.expireInvitations(now);
        return new JobResult(expired, expired == 0 ? "Không có lời mời quá hạn" : "Đã chuyển " + expired + " lời mời sang Hết hạn");
    }
}
