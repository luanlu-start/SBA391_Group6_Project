package com.fptpost.modules.moderation.model;

import com.fptpost.modules.moderation.enums.ViolationReason;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "violation_reports",
        uniqueConstraints = @UniqueConstraint(name = "uq_violation_reports_reporter", columnNames = {"case_id", "reporter_id"}),
        indexes = @Index(name = "idx_violation_reports_reporter", columnList = "reporter_id, created_at"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViolationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ViolationReason reason;

    @Column(length = 1000, columnDefinition = "nvarchar(1000)")
    private String description;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;
}
