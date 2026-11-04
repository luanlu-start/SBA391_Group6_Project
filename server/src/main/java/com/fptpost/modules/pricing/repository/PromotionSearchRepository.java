package com.fptpost.modules.pricing.repository;

import com.fptpost.modules.pricing.dto.request.PromotionSearchRequest;
import com.fptpost.modules.pricing.enums.*;
import com.fptpost.modules.pricing.model.Promotion;
import com.fptpost.modules.pricing.model.PromotionUsage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository @RequiredArgsConstructor
public class PromotionSearchRepository {
    private final EntityManager entities;

    public record Row(Promotion promotion, long usedCount) {}

    public Page<Row> find(PromotionSearchRequest request, Instant now) {
        CriteriaBuilder cb = entities.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Promotion> counted = countQuery.from(Promotion.class);
        countQuery.select(cb.count(counted)).where(filters(request, now, countQuery, counted, cb));
        long total = entities.createQuery(countQuery).getSingleResult();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Promotion> promotion = query.from(Promotion.class);
        query.multiselect(promotion, usages(query, promotion, cb)).where(filters(request, now, query, promotion, cb))
                .orderBy(cb.desc(promotion.get("createdAt")), cb.desc(promotion.get("id")));
        List<Row> rows = entities.createQuery(query).setFirstResult(request.getPage() * request.getSize())
                .setMaxResults(request.getSize()).getResultList().stream()
                .map(tuple -> new Row(tuple.get(0, Promotion.class), tuple.get(1, Long.class))).toList();
        return new PageImpl<>(rows, PageRequest.of(request.getPage(), request.getSize()), total);
    }

    private static Predicate[] filters(PromotionSearchRequest request, Instant now, CriteriaQuery<?> query,
                                       Root<Promotion> promotion, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();
        if (request.getQ() != null && !request.getQ().isBlank()) {
            String literal = request.getQ().trim().toUpperCase(Locale.ROOT).replace("!", "!!")
                    .replace("%", "!%").replace("_", "!_").replace("[", "![");
            predicates.add(cb.like(promotion.get("code"), "%" + literal + "%", '!'));
        }
        if (request.getStatus() != null) {
            var enabled = cb.equal(promotion.get("status"), PromotionStatus.ACTIVE);
            var current = cb.and(cb.lessThanOrEqualTo(promotion.get("validFrom"), now),
                    cb.greaterThan(promotion.get("validTo"), now));
            predicates.add(switch (request.getStatus()) {
                case PAUSED -> cb.equal(promotion.get("status"), PromotionStatus.PAUSED);
                case EXPIRED -> cb.and(enabled, cb.lessThanOrEqualTo(promotion.get("validTo"), now));
                case SCHEDULED -> cb.and(enabled, cb.greaterThan(promotion.get("validFrom"), now));
                case EXHAUSTED -> cb.and(enabled, current, cb.ge(usages(query, promotion, cb), promotion.get("usageLimit")));
                case ACTIVE -> cb.and(enabled, current, cb.lt(usages(query, promotion, cb), promotion.get("usageLimit")));
            });
        }
        return predicates.toArray(Predicate[]::new);
    }

    private static Subquery<Long> usages(CriteriaQuery<?> query, Root<Promotion> promotion, CriteriaBuilder cb) {
        Subquery<Long> count = query.subquery(Long.class);
        Root<PromotionUsage> usage = count.from(PromotionUsage.class);
        return count.select(cb.count(usage)).where(cb.equal(usage.get("promotion").get("id"), promotion.get("id")));
    }
}
