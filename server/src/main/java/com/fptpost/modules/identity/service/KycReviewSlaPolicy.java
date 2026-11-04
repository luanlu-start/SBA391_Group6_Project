package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.identity.exception.KycReviewErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service @RequiredArgsConstructor
public class KycReviewSlaPolicy {
    private static final Instant SQL_MIN = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant SQL_MAX = Instant.parse("9999-12-31T23:59:59.999999Z");
    private final ParameterService parameters;

    public long hours() {
        try {
            long hours = parameters.getSnapshot().integer("KYC_SLA");
            if (hours <= 0) throw new IllegalArgumentException();
            return hours;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    public Instant dueAt(Instant submittedAt, long hours) {
        try {
            if (submittedAt == null || hours <= 0) throw new IllegalArgumentException();
            return requireSqlRange(submittedAt.plus(hours, ChronoUnit.HOURS));
        } catch (DateTimeException | ArithmeticException | IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    public Instant cutoff(Instant now, long hours) {
        try {
            if (hours <= 0) throw new IllegalArgumentException();
            return requireSqlRange(now.minus(hours, ChronoUnit.HOURS));
        } catch (DateTimeException | ArithmeticException | IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private AppException unavailable() { return new AppException(KycReviewErrorCode.SLA_UNAVAILABLE); }
    private Instant requireSqlRange(Instant value) {
        if (value.isBefore(SQL_MIN) || value.isAfter(SQL_MAX)) throw unavailable();
        return value;
    }
}
