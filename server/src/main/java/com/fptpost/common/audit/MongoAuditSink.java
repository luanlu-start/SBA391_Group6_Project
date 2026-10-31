package com.fptpost.common.audit;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoAuditSink {
    private final MongoTemplate mongo;

    public MongoAuditSink(MongoTemplate mongo) { this.mongo = mongo; }

    public void append(AuditEvent event) {
        MongoAuditDocument document = MongoAuditDocument.from(event);
        try {
            mongo.insert(document);
        } catch (DuplicateKeyException duplicate) {
            // A crash between Mongo insert and SQL ack is safe. Other unique-key collisions are failures.
            MongoAuditDocument stored = mongo.findById(document.id(), MongoAuditDocument.class);
            if (!document.equals(stored)) throw duplicate;
        }
    }
}
