package com.fptpost.modules.identity.model;

import com.fptpost.modules.identity.enums.OtpType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Check;

@Entity
@Table(name = "otp_codes", indexes = @Index(name = "ix_otp_codes_user_purpose",
        columnList = "user_id, purpose, created_at DESC"))
@Check(constraints = "tries >= 0")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "uniqueidentifier")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "nvarchar(254)")
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private OtpType type;

    @Column(name = "code_hash", nullable = false, length = 60)
    private String codeHash;

    @Column(name = "expires_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;

    @Column(nullable = false)
    private int tries;

    @Column(name = "used_at", columnDefinition = "datetime2(6)")
    private Instant usedAt;

    @Version
    private long version;
}
