package com.fptpost.modules.audit.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.audit.dto.response.AuditFilterOptionsResponse;
import com.fptpost.modules.audit.dto.response.AuditFilterOptionsResponse.Option;
import com.fptpost.modules.audit.exception.AuditErrorCode;
import com.fptpost.modules.audit.repository.AuditFilterOptionsRepository;
import com.fptpost.modules.identity.service.UserDirectoryLookup;
import com.mongodb.MongoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditFilterOptionsService {
    private final AuditFilterOptionsRepository repository;
    private final UserDirectoryLookup users;

    @PreAuthorize("hasRole('ADMIN')")
    public AuditFilterOptionsResponse options(String field, String query, String entityType, String requestedLimit) {
        int limit = validate(field, query, entityType, requestedLimit);
        String text = query == null ? "" : query.trim();
        String context = entityType == null ? null : entityType.trim();
        try {
            List<Option> found;
            if (field.equals("actor")) {
                found = new ArrayList<>(users.search(text, limit + 1).stream()
                        .map(user -> new Option(user.publicId().toString(),
                                user.fullName() == null || user.fullName().isBlank() ? user.email() : user.fullName(),
                                user.email())).toList());
                String exactId = canonicalUuid(text);
                if (exactId != null && found.stream().noneMatch(option -> option.value().equals(exactId))
                        && repository.containsActor(exactId)) {
                    found.addFirst(new Option(exactId, "Người dùng " + exactId,
                            "Tài khoản không còn trong danh sách người dùng"));
                }
            } else {
                found = repository.distinct(field, text, context, limit + 1).stream()
                        .map(value -> new Option(value, value, null)).toList();
            }
            return new AuditFilterOptionsResponse(List.copyOf(found.subList(0, Math.min(found.size(), limit))),
                    found.size() > limit);
        } catch (DataAccessException | MongoException exception) {
            log.warn("Audit filter options unavailable exceptionType={}", exception.getClass().getSimpleName());
            throw new AppException(AuditErrorCode.AUDIT_STORE_UNAVAILABLE);
        }
    }

    private static int validate(String field, String query, String entityType, String requestedLimit) {
        int limit;
        try {
            limit = Integer.parseInt(requestedLimit);
        } catch (NumberFormatException exception) {
            throw new AppException(AuditErrorCode.AUDIT_INVALID_FILTER);
        }
        if (field == null || !List.of("action", "entityType", "entityId", "actor").contains(field)
                || query != null && query.length() > 100 || entityType != null && entityType.length() > 100
                || limit < 1 || limit > 50) {
            throw new AppException(AuditErrorCode.AUDIT_INVALID_FILTER);
        }
        return limit;
    }

    private static String canonicalUuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equalsIgnoreCase(value) ? uuid.toString() : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
