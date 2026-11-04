package com.fptpost.modules.identity.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.UUID;
import com.fptpost.modules.identity.enums.KycStatus;

@Entity @Table(name = "kyc_requests", indexes = {@Index(name = "ix_kyc_user_submitted", columnList = "user_id, submitted_at"),
        @Index(name = "ix_kyc_identity_status", columnList = "id_number_hash, status"),
        @Index(name = "ix_kyc_review_queue", columnList = "status, submitted_at, id")})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class KycRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "uniqueidentifier") private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "id_number_hash", nullable = false, length = 64) private String idNumberHash;
    @Column(name = "id_number_enc", nullable = false, length = 1000) private String idNumberEnc;
    @Column(name = "id_last_four", nullable = false, length = 4) private String idLastFour;
    @Column(name = "full_name", nullable = false, columnDefinition = "nvarchar(100)") private String fullName;
    @Column(nullable = false) private LocalDate dob;
    @Column(name = "issued_date", nullable = false) private LocalDate issuedDate;
    @Column(name = "expiry_date", nullable = false) private LocalDate expiryDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private KycStatus status;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewer_id") private User reviewer;
    @Column(name = "reject_reason", columnDefinition = "nvarchar(500)") private String rejectReason;
    @Column(name = "submitted_at", nullable = false, columnDefinition = "datetime2(6)") private Instant submittedAt;
    @Column(name = "reviewed_at", columnDefinition = "datetime2(6)") private Instant reviewedAt;
    @Column(name = "consent_version", nullable = false, length = 50) private String consentVersion;
    @Column(name = "consent_ip", nullable = false, length = 45) private String consentIp;
    @Column(nullable = false, columnDefinition = "bit default 0") private boolean flagged;
    @Column(name = "flag_reason", columnDefinition = "nvarchar(500)") private String flagReason;
    @Column(name = "sla_alerted_hours") private Long slaAlertedHours;
    @Column(name = "sla_alerted_at", columnDefinition = "datetime2(6)") private Instant slaAlertedAt;
    @Version private long version;
}
