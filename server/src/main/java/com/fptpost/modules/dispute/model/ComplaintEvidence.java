package com.fptpost.modules.dispute.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Ảnh bằng chứng gắn với một tin trao đổi; tệp nằm trong kho riêng tư, không có đường dẫn công khai. */
@Entity
@Table(name = "complaint_evidence", indexes = {
        @Index(name = "idx_complaint_evidence_complaint", columnList = "complaint_id"),
        @Index(name = "idx_complaint_evidence_message", columnList = "message_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID publicId;

    @Column(name = "complaint_id", nullable = false, updatable = false)
    private Long complaintId;

    @Column(name = "message_id", updatable = false)
    private Long messageId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Column(name = "storage_key", nullable = false, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID storageKey;

    @Column(name = "media_type", nullable = false, length = 50, updatable = false)
    private String mediaType;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;
}
