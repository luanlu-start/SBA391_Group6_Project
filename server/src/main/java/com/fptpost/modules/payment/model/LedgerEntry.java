package com.fptpost.modules.payment.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity bút toán kép sổ cái (DB-027: ledger_entries).
 * Quy tắc: Tổng Nợ (debit) = Tổng Có (credit) cho mỗi LedgerTransaction.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "idx_ledger_entries_txn_id", columnList = "transaction_id"),
        @Index(name = "idx_ledger_entries_account", columnList = "account_code"),
        @Index(name = "idx_ledger_entries_created_at", columnList = "created_at")
})
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private LedgerTransaction transaction;

    @Column(name = "account_code", nullable = false, length = 100)
    private String accountCode;

    @Column(name = "account_type", length = 30)
    private String accountType;

    @Builder.Default
    @Column(name = "debit", nullable = false)
    private Long debit = 0L;

    @Builder.Default
    @Column(name = "credit", nullable = false)
    private Long credit = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.debit == null) {
            this.debit = 0L;
        }
        if (this.credit == null) {
            this.credit = 0L;
        }
    }
}
