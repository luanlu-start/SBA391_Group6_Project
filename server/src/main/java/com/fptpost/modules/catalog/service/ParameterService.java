package com.fptpost.modules.catalog.service;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.catalog.dto.internal.ParameterSnapshot;
import com.fptpost.modules.catalog.dto.request.ParameterBatchUpdateRequest;
import com.fptpost.modules.catalog.dto.request.ParameterUpdateRequest;
import com.fptpost.modules.catalog.dto.response.ParameterHistoryResponse;
import com.fptpost.modules.catalog.dto.response.ParameterResponse;
import com.fptpost.modules.catalog.exception.ParameterErrorCode;
import com.fptpost.modules.catalog.mapper.ParameterMapper;
import com.fptpost.modules.catalog.model.ParameterHistory;
import com.fptpost.modules.catalog.model.SystemParameter;
import com.fptpost.modules.catalog.repository.ParameterHistoryRepository;
import com.fptpost.modules.catalog.repository.SystemParameterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParameterService {
    private final SystemParameterRepository systemParameterRepository;
    private final ParameterHistoryRepository parameterHistoryRepository;
    private final ParameterCatalog parameterCatalog;
    private final ParameterMapper parameterMapper;
    private final AuditOutboxService auditOutboxService;
    private final Clock clock;

    @PreAuthorize("hasRole('ADMIN')")
    public List<ParameterResponse> list() {
        return systemParameterRepository.findAllByOrderByPrmIdAsc().stream().map(parameterMapper::toResponse).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ParameterResponse update(String key, ParameterUpdateRequest request) {
        return updateAll(Map.of(key, request)).getFirst();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public List<ParameterResponse> updateBatch(ParameterBatchUpdateRequest request) {
        return updateAll(request.getChanges());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<ParameterHistoryResponse> history(String key, int page, int size) {
        parameterCatalog.definition(key);
        if (page < 0 || size < 1 || size > 100) throw new AppException(GlobalErrorCode.INVALID_REQUEST);
        return PageResponse.from(parameterHistoryRepository.findByParameterKey(key,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("version"), Sort.Order.desc("id"))))
                .map(parameterMapper::toResponse));
    }

    /** Domain facade: snapshots are immutable and are safe to persist on an existing order. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ParameterSnapshot getSnapshot() {
        return snapshot(systemParameterRepository.findAllByOrderByPrmIdAsc(), clock.instant());
    }

    /** Preview overrides do not update SQL, version, history, or audit. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ParameterSnapshot proposedSnapshot(Map<String, String> overrides) {
        ParameterSnapshot current = getSnapshot();
        Map<String, String> proposed = new LinkedHashMap<>(current.values());
        if (overrides != null) overrides.forEach((key, value) -> proposed.put(key, parameterCatalog.normalize(key, value)));
        parameterCatalog.validateSnapshot(proposed);
        return new ParameterSnapshot(proposed, current.versions(), current.capturedAt());
    }

    private List<ParameterResponse> updateAll(Map<String, ParameterUpdateRequest> changes) {
        if (changes == null || changes.isEmpty() || changes.size() > 109) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST);
        }
        UUID actor = actorId();
        List<SystemParameter> locked = systemParameterRepository.lockCatalog();
        Instant changedAt = clock.instant();
        ParameterSnapshot current = snapshot(locked, changedAt);
        Map<String, SystemParameter> parameters = new LinkedHashMap<>();
        locked.forEach(parameter -> parameters.put(parameter.getKey(), parameter));
        Map<String, String> proposed = new LinkedHashMap<>(current.values());
        Map<String, String> normalized = new TreeMap<>();
        changes.forEach((key, request) -> {
            parameterCatalog.definition(key);
            SystemParameter parameter = parameters.get(key);
            if (parameter == null) throw new AppException(ParameterErrorCode.PARAMETER_NOT_FOUND);
            if (request == null || request.getExpectedVersion() == null || request.getExpectedVersion() < 0) {
                throw new AppException(GlobalErrorCode.INVALID_REQUEST);
            }
            if (!request.getExpectedVersion().equals(parameter.getVersion())) {
                throw new AppException(ParameterErrorCode.PARAMETER_VERSION_CONFLICT);
            }
            String value = parameterCatalog.normalize(key, request.getValue());
            proposed.put(key, value);
            normalized.put(key, value);
        });
        parameterCatalog.validateSnapshot(proposed);

        List<ParameterResponse> responses = new ArrayList<>();
        for (var change : normalized.entrySet()) {
            SystemParameter parameter = parameters.get(change.getKey());
            if (!parameter.getValue().equals(change.getValue())) {
                Map<String, Object> before = auditState(parameter);
                String oldValue = parameter.getValue();
                parameter.setValue(change.getValue());
                parameter.setEffectiveFrom(changedAt);
                parameter.setUpdatedAt(changedAt);
                parameter.setUpdatedBy(actor);
                systemParameterRepository.saveAndFlush(parameter);
                parameterHistoryRepository.saveAndFlush(ParameterHistory.builder()
                        .publicId(UUID.randomUUID()).parameter(parameter).oldValue(oldValue).newValue(parameter.getValue())
                        .changedBy(actor).changedAt(changedAt).version(parameter.getVersion()).build());
                auditOutboxService.enqueue("PARAMETER_UPDATED", "SYSTEM_PARAMETER", parameter.getKey(),
                        before, auditState(parameter));
            }
            responses.add(parameterMapper.toResponse(parameter));
        }
        return List.copyOf(responses);
    }

    private ParameterSnapshot snapshot(List<SystemParameter> rows, Instant capturedAt) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, Long> versions = new LinkedHashMap<>();
        for (SystemParameter parameter : rows) {
            values.put(parameter.getKey(), parameter.getValue());
            versions.put(parameter.getKey(), parameter.getVersion());
        }
        if (!values.keySet().equals(parameterCatalog.defaultValues().keySet())) {
            throw new IllegalStateException("Required parameter catalog is not fully initialized");
        }
        return new ParameterSnapshot(values, versions, capturedAt);
    }

    private Map<String, Object> auditState(SystemParameter parameter) {
        return Map.of("key", parameter.getKey(), "prmId", parameter.getPrmId(), "value", parameter.getValue(),
                "version", parameter.getVersion(), "effectiveFrom", parameter.getEffectiveFrom().toString());
    }

    private UUID actorId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(jwt.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
    }
}
