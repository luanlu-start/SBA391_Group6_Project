package com.fptpost.modules.identity.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
import com.fptpost.modules.identity.enums.*;

@Entity @Table(name = "account_data_requests", indexes = @Index(name = "ix_data_requests_user", columnList = "user_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AccountDataRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "uniqueidentifier") private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private AccountRequestType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AccountRequestStatus status;
    @Column(columnDefinition = "nvarchar(500)") private String reason;
    @Column(name = "requested_at", nullable = false, columnDefinition = "datetime2(6)") private Instant requestedAt;
    @Column(name = "due_at", columnDefinition = "datetime2(6)") private Instant dueAt;
}
