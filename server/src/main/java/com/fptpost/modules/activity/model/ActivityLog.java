package com.fptpost.modules.activity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "activity_logs")
@CompoundIndex(name = "entity_history", def = "{'entityId': 1, 'occurredAt': -1, '_id': 1}")
@CompoundIndex(name = "activity_timeline", def = "{'occurredAt': -1, '_id': 1}")
public class ActivityLog {
    @Id
    private String id;
    private String entityType;
    private String entityId;
    private String action;
    private String entityName;
    private Instant occurredAt;
}
