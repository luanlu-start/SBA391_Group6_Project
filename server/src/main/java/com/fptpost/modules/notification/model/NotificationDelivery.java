package com.fptpost.modules.notification.model;

import com.fptpost.modules.notification.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "notification_deliveries", uniqueConstraints = @UniqueConstraint(name = "uq_notification_event_channel", columnNames = {"event_id", "channel"}),
        indexes = @Index(name = "ix_notification_delivery_due", columnList = "status, channel, next_attempt_at"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationDelivery {
    @Id @Column(columnDefinition = "uniqueidentifier") private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "event_id", nullable = false, updatable = false) private NotificationEvent event;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15, updatable = false) private NotificationChannel channel;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private NotificationDeliveryStatus status;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "datetime2(6)") private Instant nextAttemptAt;
    @Column(name = "claimed_at", columnDefinition = "datetime2(6)") private Instant claimedAt;
    @Column(name = "claim_token", columnDefinition = "uniqueidentifier") private UUID claimToken;
    @Column(name = "completed_at", columnDefinition = "datetime2(6)") private Instant completedAt;
    @Column(name = "last_failure", length = 80) private String lastFailure;
    @Version private long version;

    public boolean eligible(Instant now, Instant leaseCutoff) {
        return status == NotificationDeliveryStatus.PENDING && !nextAttemptAt.isAfter(now)
                || status == NotificationDeliveryStatus.PROCESSING && claimedAt != null && claimedAt.isBefore(leaseCutoff);
    }
}
