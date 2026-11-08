package com.fptpost.modules.dispute.service;

import com.fptpost.modules.catalog.service.ParameterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Hạn phản hồi, hạn xử lý và hạn kháng nghị khiếu nại theo PRM-104, PRM-105, PRM-106 (BR-R-078). */
@Component
@RequiredArgsConstructor
public class ComplaintDeadlines {

    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ParameterService parameterService;

    public Instant firstResponseDue(Instant openedAt) {
        return openedAt.plus(Duration.ofHours(parameterService.getSnapshot().integer("DISPUTE_RESP")));
    }

    /** Cộng số ngày làm việc (thứ Hai đến thứ Sáu, giờ Việt Nam), giữ nguyên giờ mở hồ sơ. */
    public Instant resolveDue(Instant openedAt) {
        long days = parameterService.getSnapshot().integer("DISPUTE_RESOLVE");
        ZonedDateTime cursor = openedAt.atZone(VIETNAM);
        while (days > 0) {
            cursor = cursor.plusDays(1);
            DayOfWeek day = cursor.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) days--;
        }
        return cursor.toInstant();
    }

    public Instant appealDeadline(Instant ruledAt) {
        return ruledAt.plus(Duration.ofHours(parameterService.getSnapshot().integer("APPEAL_WINDOW")));
    }
}
