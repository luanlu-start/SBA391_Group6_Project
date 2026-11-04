package com.fptpost.modules.payment.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity giao dịch sổ cái (DB-026: ledger_transactions).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = "entries")
@Entity
@Table(name = "ledger_transactions", indexes = {
        @Index(name = "idx_ledger_txn_code", columnList = "transaction_code", unique = true),
        @Index(name = "idx_ledger_idempotency", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_ledger_order_id", columnList = "order_id"),
        @Index(name = "idx_ledger_created_at", columnList = "created_at")
})
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_code", nullable = false, unique = true, length = 100)
    private String transactionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private LedgerTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LedgerTransactionStatus status;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "ref_type", length = 50)
    private String refType;

    @Column(name = "ref_id", length = 100)
    private String refId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LedgerEntry> entries = new ArrayList<>();

    public void addEntry(LedgerEntry entry) {
        entries.add(entry);
        entry.setTransaction(this);
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = LedgerTransactionStatus.COMPLETED;
        }
    }
}
