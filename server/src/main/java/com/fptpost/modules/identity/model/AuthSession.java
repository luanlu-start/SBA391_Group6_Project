package com.fptpost.modules.identity.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions",
        indexes = {
                @Index(name = "ix_sessions_user_expiry", columnList = "user_id, expires_at"),
                @Index(name = "ix_sessions_user_browser", columnList = "user_id, browser_id"),
                @Index(name = "ix_sessions_expiry", columnList = "expires_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "browser_id", columnDefinition = "uniqueidentifier")
    private UUID browserId;

    @Column(name = "device_info", nullable = false, columnDefinition = "nvarchar(255)")
    private String deviceInfo;

    @Column(nullable = false, length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant expiresAt;

    @Column(name = "remember_me", nullable = false, columnDefinition = "bit default 0")
    private boolean rememberMe;

    @Column(name = "revoked_at", columnDefinition = "datetime2(6)")
    private Instant revokedAt;

    @Column(name = "replaced_by", columnDefinition = "uniqueidentifier")
    private UUID replacedBy;

    @Version
    private long version;
}
