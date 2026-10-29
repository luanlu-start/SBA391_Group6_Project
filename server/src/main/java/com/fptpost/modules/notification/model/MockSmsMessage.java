package com.fptpost.modules.notification.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/** Development SMS receipt; full rendered content is retained without logging contact details. */
@Entity @Table(name = "notification_mock_sms") @Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class MockSmsMessage {
    @Id @Column(columnDefinition = "uniqueidentifier") private UUID id;
    @Column(name = "recipient_id", nullable = false, columnDefinition = "uniqueidentifier") private UUID recipient;
    @Column(name = "phone_last_four", nullable = false, length = 4) private String phoneLastFour;
    @Column(nullable = false, columnDefinition = "nvarchar(2000)") private String body;
    @Column(nullable = false, columnDefinition = "datetime2(6)") private Instant at;
}
