package com.fptpost.modules.guarantee.model;

import com.fptpost.modules.guarantee.enums.CompensationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Khoản bồi thường từ Quỹ bảo đảm theo phán quyết khiếu nại (DB-041). */
@Entity
@Table(name = "compensations", indexes = {
        @Index(name = "idx_compensations_status", columnList = "status, ruled_at"),
        @Index(name = "idx_compensations_beneficiary", columnList = "beneficiary_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Compensation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID publicId;

    @Column(name = "complaint_id", nullable = false, unique = true, updatable = false)
    private Long complaintId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "beneficiary_id", nullable = false, updatable = false)
    private Long beneficiaryId;

    @Column(name = "requested_amount", nullable = false, updatable = false)
    private Long requestedAmount;

    @Column(name = "approved_amount")
    private Long approvedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompensationStatus status;

    /** Thời điểm phán quyết, dùng làm thứ tự chi khi quỹ không đủ (BR-R-079). */
    @Column(name = "ruled_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant ruledAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at", columnDefinition = "datetime2(6)")
    private Instant reviewedAt;

    @Column(length = 500, columnDefinition = "nvarchar(500)")
    private String note;

    @Column(name = "queued_at", columnDefinition = "datetime2(6)")
    private Instant queuedAt;

    @Column(name = "paid_at", columnDefinition = "datetime2(6)")
    private Instant paidAt;

    @Column(name = "ledger_transaction_id")
    private Long ledgerTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant updatedAt;

    @Version
    private long version;
}
