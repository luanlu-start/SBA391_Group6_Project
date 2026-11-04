package com.fptpost.modules.pricing.service;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.identity.dto.response.UserDirectoryEntry;
import com.fptpost.modules.identity.service.UserDirectoryLookup;
import com.fptpost.modules.pricing.dto.request.*;
import com.fptpost.modules.pricing.dto.response.*;
import com.fptpost.modules.pricing.enums.*;
import com.fptpost.modules.pricing.exception.PromotionErrorCode;
import com.fptpost.modules.pricing.model.Promotion;
import com.fptpost.modules.pricing.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class PromotionService {
    private static final Instant MIN_SQL_TIME = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_SQL_TIME = Instant.parse("9999-12-31T23:59:59.999999Z");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999");
    private final PromotionRepository promotions;
    private final PromotionUsageRepository usages;
    private final PromotionSearchRepository search;
    private final UserDirectoryLookup users;
    private final AuditOutboxService audit;
    private final Clock clock;

    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<PromotionResponse> list(PromotionSearchRequest request) {
        if (request == null || request.getPage() < 0 || request.getPage() > 100000 || request.getSize() < 1
                || request.getSize() > 100 || request.getQ() != null && request.getQ().length() > 50) invalid();
        Instant now = clock.instant();
        var page = search.find(request, now);
        Set<UUID> allTargets = page.stream().flatMap(row -> row.promotion().getTargetUserIds().stream()).collect(Collectors.toSet());
        Map<UUID, UserDirectoryEntry> directory = directory(allTargets);
        return PageResponse.from(page.map(row -> response(row.promotion(), row.usedCount(), now, directory)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public PromotionResponse detail(UUID id) {
        Promotion promotion = require(id);
        return response(promotion, usages.countByPromotionId(id), clock.instant(), directory(promotion.getTargetUserIds()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public PromotionTargetOptionsResponse targetOptions(String query, int limit) {
        if (query != null && query.length() > 100 || limit < 1 || limit > 50) invalid();
        List<UserDirectoryEntry> options = users.search(query, limit + 1);
        return new PromotionTargetOptionsResponse(options.stream().limit(limit).map(PromotionService::targetUser).toList(),
                options.size() > limit);
    }

    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public PromotionResponse create(PromotionWriteRequest request) {
        Normalized normalized = normalize(request);
        if (promotions.existsByCode(normalized.code())) throw new AppException(PromotionErrorCode.CODE_ALREADY_EXISTS);
        Map<UUID, UserDirectoryEntry> directory = validateTargets(normalized.targets(), Set.of());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Promotion promotion = Promotion.builder().id(UUID.randomUUID()).code(normalized.code()).createdAt(now).build();
        apply(promotion, request, normalized, now);
        try {
            promotions.saveAndFlush(promotion);
        } catch (DataIntegrityViolationException exception) {
            // The only race not covered by validation is the unique canonical code constraint.
            throw new AppException(PromotionErrorCode.CODE_ALREADY_EXISTS);
        }
        audit.enqueue("PROMOTION_CREATED", "PROMOTION", promotion.getId().toString(), Map.of(), auditState(promotion, 0));
        return response(promotion, 0, now, directory);
    }

    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public PromotionResponse update(UUID id, PromotionWriteRequest request) {
        Normalized normalized = normalize(request);
        if (request.getExpectedVersion() == null || request.getExpectedVersion() < 0) invalid();
        Promotion promotion = promotions.findByIdForUpdate(id).orElseThrow(() -> new AppException(PromotionErrorCode.NOT_FOUND));
        if (!request.getExpectedVersion().equals(promotion.getVersion())) throw new AppException(PromotionErrorCode.VERSION_CONFLICT);
        if (!promotion.getCode().equals(normalized.code())) throw new AppException(PromotionErrorCode.CODE_IMMUTABLE);
        long used = usages.countByPromotionId(id);
        if (request.getUsageLimit() < used) throw new AppException(PromotionErrorCode.USAGE_LIMIT_BELOW_USED);
        Map<UUID, UserDirectoryEntry> directory = validateTargets(normalized.targets(), promotion.getTargetUserIds());
        Map<String, Object> before = auditState(promotion, used);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        apply(promotion, request, normalized, now);
        promotions.saveAndFlush(promotion);
        audit.enqueue("PROMOTION_UPDATED", "PROMOTION", id.toString(), before, auditState(promotion, used));
        return response(promotion, used, now, directory);
    }

    private Promotion require(UUID id) {
        if (id == null) throw new AppException(PromotionErrorCode.NOT_FOUND);
        return promotions.findById(id).orElseThrow(() -> new AppException(PromotionErrorCode.NOT_FOUND));
    }

    private static Normalized normalize(PromotionWriteRequest request) {
        if (request == null || request.getCode() == null || request.getCode().length() > 50 || request.getType() == null
                || request.getStatus() == null || request.getTarget() == null || request.getValue() == null
                || request.getValue().length() > 32 || request.getUsageLimit() == null || request.getUsageLimit() < 1
                || !validTime(request.getValidFrom()) || !validTime(request.getValidTo())) invalid();
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9_-]{3,50}")) invalid();
        String raw = request.getValue().trim();
        if (!raw.matches("[0-9]+(?:\\.[0-9]{1,2})?")) invalid();
        BigDecimal value = new BigDecimal(raw).stripTrailingZeros();
        if (value.signum() <= 0 || value.compareTo(MAX_AMOUNT) > 0 || request.getType() == PromotionType.FIXED_AMOUNT
                && value.scale() > 0 || request.getType() == PromotionType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) invalid();
        Instant from = request.getValidFrom().truncatedTo(ChronoUnit.MICROS);
        Instant to = request.getValidTo().truncatedTo(ChronoUnit.MICROS);
        if (!from.isBefore(to)) invalid();
        List<UUID> targets = request.getTargetUserIds();
        if (targets == null || targets.size() > 100 || targets.stream().anyMatch(Objects::isNull)
                || new HashSet<>(targets).size() != targets.size()
                || request.getTarget() == PromotionTarget.ALL && !targets.isEmpty()
                || request.getTarget() == PromotionTarget.SELECTED_USERS && targets.isEmpty()) {
            throw new AppException(PromotionErrorCode.TARGET_INVALID);
        }
        return new Normalized(code, value, from, to, Set.copyOf(targets));
    }

    private static boolean validTime(Instant time) {
        return time != null && !time.isBefore(MIN_SQL_TIME) && !time.isAfter(MAX_SQL_TIME);
    }

    private Map<UUID, UserDirectoryEntry> validateTargets(Set<UUID> targets, Set<UUID> previous) {
        Map<UUID, UserDirectoryEntry> directory = directory(targets);
        if (targets.stream().anyMatch(id -> !previous.contains(id) && !directory.containsKey(id))) {
            throw new AppException(PromotionErrorCode.TARGET_INVALID);
        }
        return directory;
    }

    private Map<UUID, UserDirectoryEntry> directory(Set<UUID> ids) {
        List<UUID> all = new ArrayList<>(ids);
        Map<UUID, UserDirectoryEntry> result = new HashMap<>();
        for (int start = 0; start < all.size(); start += 1000) {
            users.lookup(all.subList(start, Math.min(start + 1000, all.size())))
                    .forEach(user -> result.put(user.publicId(), user));
        }
        return result;
    }

    private static void apply(Promotion promotion, PromotionWriteRequest request, Normalized normalized, Instant now) {
        promotion.setType(request.getType()); promotion.setValue(normalized.value());
        promotion.setValidFrom(normalized.from()); promotion.setValidTo(normalized.to());
        promotion.setUsageLimit(request.getUsageLimit()); promotion.setTarget(request.getTarget());
        promotion.getTargetUserIds().clear(); promotion.getTargetUserIds().addAll(normalized.targets());
        promotion.setStatus(request.getStatus()); promotion.setUpdatedAt(now);
    }

    private static PromotionResponse response(Promotion promotion, long used, Instant now, Map<UUID, UserDirectoryEntry> directory) {
        List<UUID> ids = promotion.getTargetUserIds().stream().sorted().toList();
        List<PromotionResponse.TargetUser> targets = ids.stream().map(id -> directory.containsKey(id)
                ? targetUser(directory.get(id)) : new PromotionResponse.TargetUser(id.toString(), "Người dùng " + id,
                        "Tài khoản không còn trong danh sách người dùng")).toList();
        return new PromotionResponse(promotion.getId(), promotion.getCode(), promotion.getType(),
                promotion.getValue().stripTrailingZeros().toPlainString(), promotion.getValidFrom(), promotion.getValidTo(),
                promotion.getUsageLimit(), used, promotion.getTarget(), ids, targets, promotion.getStatus(),
                PromotionState.effective(promotion, used, now), promotion.getVersion(), promotion.getCreatedAt(), promotion.getUpdatedAt());
    }

    private static PromotionResponse.TargetUser targetUser(UserDirectoryEntry user) {
        return new PromotionResponse.TargetUser(user.publicId().toString(), user.fullName() == null || user.fullName().isBlank()
                ? user.email() : user.fullName(), user.email());
    }

    private static Map<String, Object> auditState(Promotion promotion, long used) {
        return Map.ofEntries(Map.entry("code", promotion.getCode()), Map.entry("type", promotion.getType().name()),
                Map.entry("value", promotion.getValue().stripTrailingZeros().toPlainString()),
                Map.entry("validFrom", promotion.getValidFrom().toString()), Map.entry("validTo", promotion.getValidTo().toString()),
                Map.entry("usageLimit", promotion.getUsageLimit()), Map.entry("usedCount", used),
                Map.entry("target", promotion.getTarget().name()), Map.entry("targetUserIds", promotion.getTargetUserIds().stream()
                        .map(UUID::toString).sorted().toList()), Map.entry("status", promotion.getStatus().name()),
                Map.entry("version", promotion.getVersion()));
    }

    private record Normalized(String code, BigDecimal value, Instant from, Instant to, Set<UUID> targets) {}
    private static void invalid() { throw new AppException(PromotionErrorCode.INVALID_VALUE); }
}
