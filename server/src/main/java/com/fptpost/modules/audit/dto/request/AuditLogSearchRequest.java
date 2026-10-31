package com.fptpost.modules.audit.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuditLogSearchRequest {
    public static final int MAX_PAGE = 100000;

    UUID actor;
    @Size(max = 100) String action;
    @Size(max = 100) String entityType;
    @Size(max = 200) String entityId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to;
    @Min(0) @Max(MAX_PAGE) int page = 0;
    @Min(1) @Max(100) int size = 20;
}
