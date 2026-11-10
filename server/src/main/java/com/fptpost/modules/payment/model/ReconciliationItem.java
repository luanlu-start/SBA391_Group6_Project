package com.fptpost.modules.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Thực thể Dòng đối soát giao dịch (UC-43, DB-033: reconciliation_items).
 */
@Entity
@Table(name = "reconciliation_items", indexes = {
        @Index(name = "idx_reconcile_items_report_id", columnList = "report_id"),
        @Index(name = "idx_reconcile_items_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private ReconciliationReport report;

    @Column(name = "txn_ref", nullable = false, length = 100)
    private String txnRef;

    @Column(name = "kind", nullable = false, length = 40)
    private String kind;

    @Column(name = "internal_amount", nullable = false)
    private Long internalAmount;

    @Column(name = "vnpay_amount", nullable = false)
    private Long vnpayAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconciliationItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false, length = 30)
    private ReconciliationDiscrepancyType discrepancyType;

    @Column(name = "note", columnDefinition = "NVARCHAR(500)")
    private String note;

    @Column(name = "adjustment_txn_code", length = 64)
    private String adjustmentTxnCode;

    @Column(name = "adjusted_by")
    private Long adjustedBy;

    @Column(name = "adjusted_at")
    private Instant adjustedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.discrepancyType == null) {
            this.discrepancyType = ReconciliationDiscrepancyType.NONE;
        }
    }
}
