package com.fptpost.modules.moderation.model;

import com.fptpost.modules.moderation.enums.OrderModerationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "order_moderations", indexes = {
        @Index(name = "uq_order_moderations_order", columnList = "order_id", unique = true),
        @Index(name = "idx_order_moderations_status", columnList = "status, submitted_at"),
        @Index(name = "idx_order_moderations_sender", columnList = "sender_id, status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderModeration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderModerationStatus status;

    @Column(name = "matched_terms", length = 500, columnDefinition = "nvarchar(500)")
    private String matchedTerms;

    @Column(name = "category_flag", length = 20)
    private String categoryFlag;

    /** Kiểm duyệt viên đã duyệt nội dung này thì lần sửa không đổi nội dung không phải duyệt lại. */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "rejection_reason", length = 500, columnDefinition = "nvarchar(500)")
    private String rejectionReason;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewed_at", columnDefinition = "datetime2(6)")
    private Instant reviewedAt;

    @Column(name = "submitted_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant submittedAt;

    @Version
    private long version;
}
