package com.fptpost.modules.dispute.model;

import com.fptpost.modules.dispute.enums.AppealDecision;
import com.fptpost.modules.dispute.enums.ComplaintOutcome;
import com.fptpost.modules.dispute.enums.ComplaintReason;
import com.fptpost.modules.dispute.enums.ComplaintRole;
import com.fptpost.modules.dispute.enums.ComplaintStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Hồ sơ khiếu nại của một đơn (DB-039). Tiền chỉ được xử lý khi hồ sơ chuyển sang CLOSED. */
@Entity
@Table(name = "complaints", indexes = {
        @Index(name = "idx_complaints_order", columnList = "order_id, status"),
        @Index(name = "idx_complaints_queue", columnList = "status, resolve_due_at"),
        @Index(name = "idx_complaints_opened_by", columnList = "opened_by, created_at"),
        @Index(name = "idx_complaints_against", columnList = "against_user_id, created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID publicId;

    @Column(nullable = false, unique = true, updatable = false, length = 20)
    private String code;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "opened_by", nullable = false, updatable = false)
    private Long openedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "opener_role", nullable = false, length = 20, updatable = false)
    private ComplaintRole openerRole;

    @Column(name = "against_user_id", updatable = false)
    private Long againstUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplaintReason reason;

    @Column(length = 1000, columnDefinition = "nvarchar(1000)")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComplaintStatus status;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "first_response_at", columnDefinition = "datetime2(6)")
    private Instant firstResponseAt;

    @Column(name = "first_response_due_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant firstResponseDueAt;

    @Column(name = "resolve_due_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant resolveDueAt;

    @Column(name = "response_alerted_at", columnDefinition = "datetime2(6)")
    private Instant responseAlertedAt;

    @Column(name = "resolve_alerted_at", columnDefinition = "datetime2(6)")
    private Instant resolveAlertedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ComplaintOutcome outcome;

    /** Phần trăm ký quỹ hoàn cho người gửi khi chia theo tỷ lệ. */
    @Column(name = "sender_percent")
    private Integer senderPercent;

    /** Số tiền hoàn cho người gửi: lấy từ ký quỹ đang giữ, hoặc thu hồi từ người giao nếu ký quỹ đã giải ngân. */
    @Column(name = "refund_amount")
    private Long refundAmount;

    /** Phần ký quỹ đang giữ được trả cho người giao. */
    @Column(name = "courier_amount")
    private Long courierAmount;

    /** Bồi thường đề nghị chi từ Quỹ bảo đảm, chỉ cho đơn có bảo hiểm (BR-R-079). */
    @Column(name = "compensation_amount")
    private Long compensationAmount;

    @Column(name = "ruling_reason", length = 1000, columnDefinition = "nvarchar(1000)")
    private String rulingReason;

    @Column(name = "ruled_by")
    private Long ruledBy;

    @Column(name = "ruled_at", columnDefinition = "datetime2(6)")
    private Instant ruledAt;

    @Column(name = "appeal_deadline", columnDefinition = "datetime2(6)")
    private Instant appealDeadline;

    @Column(name = "appeal_count", nullable = false)
    private int appealCount;

    @Column(name = "appealed_by")
    private Long appealedBy;

    @Column(name = "appeal_reason", length = 1000, columnDefinition = "nvarchar(1000)")
    private String appealReason;

    @Column(name = "appealed_at", columnDefinition = "datetime2(6)")
    private Instant appealedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "appeal_decision", length = 20)
    private AppealDecision appealDecision;

    @Column(name = "appeal_note", length = 1000, columnDefinition = "nvarchar(1000)")
    private String appealNote;

    @Column(name = "appeal_reviewed_by")
    private Long appealReviewedBy;

    @Column(name = "appeal_reviewed_at", columnDefinition = "datetime2(6)")
    private Instant appealReviewedAt;

    @Column(name = "clawback_amount")
    private Long clawbackAmount;

    @Column(name = "unrecovered_amount")
    private Long unrecoveredAmount;

    @Column(name = "closed_at", columnDefinition = "datetime2(6)")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant updatedAt;

    @Version
    private long version;
}
