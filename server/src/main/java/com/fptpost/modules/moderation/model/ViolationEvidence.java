package com.fptpost.modules.moderation.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Ảnh bằng chứng lưu ở kho riêng tư; chỉ kiểm duyệt viên đọc được qua API. */
@Entity
@Table(name = "violation_evidence", indexes = @Index(name = "idx_violation_evidence_report", columnList = "report_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViolationEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID publicId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "storage_key", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID storageKey;

    @Column(name = "media_type", nullable = false, length = 30)
    private String mediaType;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;
}
