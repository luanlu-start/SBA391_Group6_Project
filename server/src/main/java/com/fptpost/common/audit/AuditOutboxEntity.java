package com.fptpost.common.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "ix_audit_outbox_pending", columnList = "status,next_attempt_at"),
        @Index(name = "ix_audit_outbox_lease", columnList = "status,claimed_at")
}, uniqueConstraints = @UniqueConstraint(name = "uq_audit_outbox_event", columnNames = "event_id"))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AuditOutboxEntity {
    public enum Status { PENDING, PROCESSING, DELIVERED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(nullable = false, length = 50) private String type;
    @Column(nullable = false, columnDefinition = "nvarchar(max)") private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(6)") private Instant createdAt;
    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "datetime2(6)") private Instant nextAttemptAt;
    @Column(name = "claimed_at", columnDefinition = "datetime2(6)") private Instant claimedAt;
    @Column(name = "claim_token") private UUID claimToken;
    @Column(nullable = false) private int attempts;
    @Column(name = "last_error", length = 200) private String lastError;
    @Column(name = "processed_at", columnDefinition = "datetime2(6)") private Instant completedAt;

    static AuditOutboxEntity pending(UUID eventId, String payload, Instant now) {
        var row = new AuditOutboxEntity();
        row.eventId = eventId;
        row.type = "AUDIT";
        row.payload = payload;
        row.createdAt = now;
        row.nextAttemptAt = now;
        row.status = Status.PENDING;
        return row;
    }

    boolean eligible(Instant now, Instant leaseCutoff) {
        return (status == Status.PENDING && !nextAttemptAt.isAfter(now))
                || (status == Status.PROCESSING && claimedAt != null && !claimedAt.isAfter(leaseCutoff));
    }

    AuditOutboxClaim claim(Instant now) {
        status = Status.PROCESSING;
        claimedAt = now;
        claimToken = UUID.randomUUID();
        if (attempts < Integer.MAX_VALUE) attempts++;
        return new AuditOutboxClaim(id, eventId, payload, claimToken);
    }

    boolean ownedBy(AuditOutboxClaim claim) {
        return status == Status.PROCESSING && claimToken != null && claimToken.equals(claim.claimToken());
    }

    void delivered(Instant now) {
        status = Status.DELIVERED;
        completedAt = now;
        claimedAt = null;
        claimToken = null;
        lastError = null;
    }

    void retry(Instant nextAttemptAt, String errorClass) {
        status = Status.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        claimedAt = null;
        claimToken = null;
        // Exception messages may contain payloads or connection credentials. Persist only the class.
        lastError = errorClass.length() <= 200 ? errorClass : errorClass.substring(0, 200);
    }
}
