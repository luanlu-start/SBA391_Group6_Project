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
public class OfferExpiryJob implements BackgroundJob {

    private final OrderMaintenanceRepository orders;

    @Override
    public String name() {
        return "offer-expiry";
    }

    @Override
    public String title() {
        return "Hết hạn chào giá";
    }

    @Override
    public String description() {
        return "Chuyển chào giá chờ phản hồi quá BID_TTL sang Hết hạn.";
    }

    @Override
    public Duration interval() {
        return Duration.ofMinutes(5);
    }

    @Override
    public JobResult execute(Instant now) {
        int expired = orders.expireOffers(now);
        return new JobResult(expired, expired == 0 ? "Không có chào giá quá hạn" : "Đã chuyển " + expired + " chào giá sang Hết hạn");
    }
}
