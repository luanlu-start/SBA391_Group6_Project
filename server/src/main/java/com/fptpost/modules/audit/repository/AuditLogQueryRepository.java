package com.fptpost.modules.audit.repository;

import com.fptpost.common.audit.MongoAuditDocument;
import com.fptpost.modules.audit.dto.request.AuditLogSearchRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Only count/find operations are exposed; the audit producer remains append-only. */
@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditLogQueryRepository {
    MongoTemplate mongoTemplate;

    public Page<MongoAuditDocument> find(AuditLogSearchRequest request) {
        List<Criteria> filters = new ArrayList<>();
        if (request.getActor() != null) filters.add(Criteria.where("actor").is(request.getActor().toString()));
        exact(filters, "action", request.getAction());
        exact(filters, "entityType", request.getEntityType());
        exact(filters, "entityId", request.getEntityId());
        if (request.getFrom() != null || request.getTo() != null) {
            Criteria at = Criteria.where("at");
            // BSON dates have millisecond precision. Rounding a lower bound down would include older events.
            if (request.getFrom() != null) at.gte(ceilToMillis(request.getFrom()));
            if (request.getTo() != null) at.lte(Instant.ofEpochMilli(request.getTo().toEpochMilli()));
            filters.add(at);
        }
        Query countQuery = new Query();
        if (!filters.isEmpty()) countQuery.addCriteria(new Criteria().andOperator(filters));
        long total = mongoTemplate.count(countQuery, MongoAuditDocument.class);
        PageRequest pageable = PageRequest.of(request.getPage(), request.getSize(),
                Sort.by(Sort.Order.desc("at"), Sort.Order.desc("eventId")));
        Query pageQuery = Query.of(countQuery).with(pageable);
        List<MongoAuditDocument> documents = mongoTemplate.find(pageQuery, MongoAuditDocument.class);
        return new PageImpl<>(documents, pageable, total);
    }

    private static void exact(List<Criteria> filters, String field, String value) {
        if (value != null && !value.isBlank()) filters.add(Criteria.where(field).is(value.trim()));
    }

    private static Instant ceilToMillis(Instant value) {
        long millis = value.toEpochMilli();
        return Instant.ofEpochMilli(value.getNano() % 1000000 == 0 ? millis : millis + 1);
    }
}
