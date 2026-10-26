package com.fptpost.modules.identity.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
import com.fptpost.modules.identity.enums.ContactType;

@Entity @Table(name = "contact_changes", indexes = @Index(name = "ix_contact_changes_user", columnList = "user_id, created_at"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ContactChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "uniqueidentifier") private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ContactType type;
    @Column(name = "new_value", nullable = false, columnDefinition = "nvarchar(254)") private String newValue;
    @Column(name = "base_email", nullable = false, columnDefinition = "nvarchar(254)") private String baseEmail;
    @Column(name = "base_phone", nullable = false, length = 16) private String basePhone;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "otp_id", nullable = false) private OtpCode otp;
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(6)") private Instant createdAt;
    @Column(name = "completed_at", columnDefinition = "datetime2(6)") private Instant completedAt;
}
