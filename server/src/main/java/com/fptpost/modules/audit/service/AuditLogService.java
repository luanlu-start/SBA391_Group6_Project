package com.fptpost.modules.audit.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.audit.dto.request.AuditLogSearchRequest;
import com.fptpost.modules.audit.dto.response.AuditLogResponse;
import com.fptpost.modules.audit.exception.AuditErrorCode;
import com.fptpost.modules.audit.mapper.AuditLogMapper;
import com.fptpost.modules.audit.repository.AuditLogQueryRepository;
import com.mongodb.MongoException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditLogService {
    AuditLogQueryRepository auditLogQueryRepository;
    AuditLogMapper auditLogMapper;

    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AuditLogResponse> search(AuditLogSearchRequest request) {
        validate(request);
        try {
            return PageResponse.from(auditLogQueryRepository.find(request).map(auditLogMapper::toResponse));
        } catch (DataAccessException | MongoException exception) {
            log.warn("Audit query unavailable exceptionType={}", exception.getClass().getSimpleName());
            throw new AppException(AuditErrorCode.AUDIT_STORE_UNAVAILABLE);
        }
    }

    private static void validate(AuditLogSearchRequest request) {
        if (request == null || request.getPage() < 0 || request.getPage() > AuditLogSearchRequest.MAX_PAGE
                || request.getSize() < 1 || request.getSize() > 100
                || tooLong(request.getAction(), 100) || tooLong(request.getEntityType(), 100)
                || tooLong(request.getEntityId(), 200) || outsideBsonRange(request.getFrom())
                || outsideBsonRange(request.getTo()) || request.getFrom() != null && request.getTo() != null
                && request.getFrom().isAfter(request.getTo())) {
            throw new AppException(AuditErrorCode.AUDIT_INVALID_FILTER);
        }
    }

    private static boolean tooLong(String value, int limit) {
        return value != null && value.length() > limit;
    }

    private static boolean outsideBsonRange(Instant value) {
        return value != null && (value.isBefore(Instant.ofEpochMilli(Long.MIN_VALUE))
                || value.isAfter(Instant.ofEpochMilli(Long.MAX_VALUE)));
    }
}
