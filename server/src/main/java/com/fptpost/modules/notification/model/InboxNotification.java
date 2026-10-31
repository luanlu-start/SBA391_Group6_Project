package com.fptpost.modules.notification.model;

import com.fptpost.modules.notification.enums.NotificationCategory;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("notifications") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InboxNotification {
    @Id private String id;
    private String userId;
    private NotificationCategory category;
    private String title;
    private String body;
    private String relativeLink;
    private Instant at;
    private Instant readAt;
}
