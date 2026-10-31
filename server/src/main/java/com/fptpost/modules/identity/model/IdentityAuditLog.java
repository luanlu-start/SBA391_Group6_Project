package com.fptpost.modules.identity.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.UUID;
import com.fptpost.modules.identity.enums.IdentityAuditAction;

@Entity @Table(name = "identity_audit_logs", indexes = @Index(name = "ix_identity_audit_actor_time", columnList = "actor_public_id, occurred_at"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class IdentityAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "actor_public_id", nullable = false, columnDefinition = "uniqueidentifier") private UUID actorPublicId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private IdentityAuditAction action;
    @Column(name = "target_public_id", columnDefinition = "uniqueidentifier") private UUID targetPublicId;
    @Column(name = "occurred_at", nullable = false, columnDefinition = "datetime2(6)") private Instant occurredAt;
}
