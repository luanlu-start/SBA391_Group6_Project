package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.dto.request.KycReviewSearchRequest;
import com.fptpost.modules.identity.enums.KycStatus;
import com.fptpost.modules.identity.model.KycRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

@Repository @RequiredArgsConstructor
public class KycReviewSearchRepository {
    private final EntityManager entities;

    public record Candidate(UUID requestId, UUID applicantId) { }

    public Page<KycRequest> find(KycReviewSearchRequest request, KycStatus status, Instant cutoff) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (status != null) {
            where.append(" and k.status = :status"); parameters.put("status", status);
        }
        if (request.getFlagged() != null) {
            where.append(" and k.flagged = :flagged"); parameters.put("flagged", request.getFlagged());
        }
        if (request.getOverdue() != null) {
            where.append(request.getOverdue()
                    ? " and k.status = :pending and k.submittedAt <= :cutoff"
                    : " and (k.status <> :pending or k.submittedAt > :cutoff)");
            parameters.put("pending", KycStatus.PENDING); parameters.put("cutoff", cutoff);
        }
        String text = request.getQ() == null ? "" : request.getQ().strip().toLowerCase(Locale.ROOT);
        if (!text.isEmpty()) {
            where.append(" and (lower(k.fullName) like :pattern escape '!' or lower(k.user.email) like :pattern escape '!'"
                    + " or k.idLastFour like :pattern escape '!'");
            parameters.put("pattern", "%" + text.replace("!", "!!").replace("%", "!%")
                    .replace("_", "!_").replace("[", "![") + "%");
            try {
                UUID id = UUID.fromString(text);
                where.append(" or k.publicId = :publicId or k.user.publicId = :publicId");
                parameters.put("publicId", id);
            } catch (IllegalArgumentException ignored) { /* Only canonical UUID searches add the exact-ID predicate. */ }
            where.append(")");
        }
        var count = entities.createQuery("select count(k) from KycRequest k" + where, Long.class);
        bind(count, parameters);
        long total = count.getSingleResult();
        var query = entities.createQuery("select k from KycRequest k join fetch k.user left join fetch k.reviewer" + where
                + " order by k.submittedAt asc, k.id asc", KycRequest.class);
        bind(query, parameters);
        var content = query.setFirstResult(request.getPage() * request.getSize()).setMaxResults(request.getSize()).getResultList();
        return new PageImpl<>(content, PageRequest.of(request.getPage(), request.getSize()), total);
    }

    public List<Candidate> overdueCandidates(Instant cutoff, long hours, int limit) {
        return entities.createQuery("select k.publicId, k.user.publicId from KycRequest k where k.status = :pending and k.submittedAt <= :cutoff"
                        + " and (k.slaAlertedHours is null or k.slaAlertedHours <> :hours) order by k.submittedAt asc, k.id asc", Object[].class)
                .setParameter("pending", KycStatus.PENDING).setParameter("cutoff", cutoff).setParameter("hours", hours)
                .setMaxResults(limit).getResultList().stream().map(row -> new Candidate((UUID) row[0], (UUID) row[1])).toList();
    }

    private static void bind(Query query, Map<String, Object> parameters) { parameters.forEach(query::setParameter); }
}
