package com.fptpost.modules.notification.model;

import com.fptpost.modules.notification.enums.NotificationCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;
import java.util.UUID;

/** Immutable SQL event and deduplication record; the user inbox is projected into MongoDB. */
@Entity @Table(name = "notification_events", uniqueConstraints = @UniqueConstraint(name = "uq_notification_recipient_event", columnNames = {"recipient_id", "event_key"}))
@Immutable @Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationEvent {
    @Id @Column(columnDefinition = "uniqueidentifier") private UUID id;
    @Column(name = "recipient_id", nullable = false, updatable = false, columnDefinition = "uniqueidentifier") private UUID recipient;
    @Column(name = "event_key", nullable = false, updatable = false, length = 160) private String eventKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 30) private NotificationCategory category;
    @Column(nullable = false, updatable = false, columnDefinition = "nvarchar(200)") private String title;
    @Column(nullable = false, updatable = false, columnDefinition = "nvarchar(2000)") private String body;
    @Column(name = "relative_link", updatable = false, length = 500) private String relativeLink;
    @Column(nullable = false, updatable = false, columnDefinition = "datetime2(6)") private Instant at;
}
