package com.fptpost.modules.notification.model;

import com.fptpost.modules.notification.enums.NotificationCategory;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity @Table(name = "notification_preferences", uniqueConstraints = @UniqueConstraint(name = "uq_notification_preference", columnNames = {"recipient_id", "category"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationPreference {
    @Id @Column(columnDefinition = "uniqueidentifier") private UUID id;
    @Column(name = "recipient_id", nullable = false, columnDefinition = "uniqueidentifier") private UUID recipient;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private NotificationCategory category;
    @Column(name = "in_app", nullable = false) private boolean inApp;
    @Column(nullable = false) private boolean email;
    @Column(nullable = false) private boolean sms;
    @Version private long version;
}
