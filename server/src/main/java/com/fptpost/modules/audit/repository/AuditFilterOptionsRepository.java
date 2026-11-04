package com.fptpost.modules.audit.repository;

import com.fptpost.common.audit.MongoAuditDocument;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Repository
@RequiredArgsConstructor
public class AuditFilterOptionsRepository {
    private final MongoTemplate mongo;

    public List<String> distinct(String field, String query, String entityType, int limit) {
        if (!List.of("action", "entityType", "entityId").contains(field) || limit < 1 || limit > 51) {
            throw new IllegalArgumentException("Invalid audit option query");
        }
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where(field).type(2).ne(""));
        int maximumLength = field.equals("entityId") ? 200 : 100;
        // Only offer values that can be submitted through the exact-match search contract.
        // Leading/trailing whitespace cannot round-trip because search normalizes its inputs.
        criteria.add(Criteria.where(field).regex(Pattern.compile("(?s)^(?=.{1," + maximumLength + "}$)\\S(?:.*\\S)?$")));
        if (!query.isBlank()) {
            criteria.add(Criteria.where(field).regex(Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE)));
        }
        if (field.equals("entityId") && entityType != null && !entityType.isBlank()) {
            criteria.add(Criteria.where("entityType").is(entityType));
        }
        var aggregation = Aggregation.newAggregation(
                Aggregation.match(new Criteria().andOperator(criteria)),
                Aggregation.group(field), Aggregation.sort(Sort.Direction.ASC, "_id"), Aggregation.limit(limit));
        return mongo.aggregate(aggregation, MongoAuditDocument.class, Document.class).getMappedResults().stream()
                .map(document -> document.getString("_id")).toList();
    }

    public boolean containsActor(String publicId) {
        return mongo.exists(Query.query(Criteria.where("actor").is(publicId)), MongoAuditDocument.class);
    }
}
