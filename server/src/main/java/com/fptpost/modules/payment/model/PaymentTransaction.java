package com.fptpost.modules.payment.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity tracking payment gateway transactions and audit records.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_payment_txn_ref", columnList = "txn_ref", unique = true),
        @Index(name = "idx_payment_order_code", columnList = "order_code"),
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_created_at", columnList = "created_at")
})
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_ref", nullable = false, unique = true, length = 100)
    private String txnRef;

    @Column(name = "order_code", nullable = false, length = 50)
    private String orderCode;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "order_info", length = 500)
    private String orderInfo;

    @Column(name = "bank_code", length = 50)
    private String bankCode;

    @Column(name = "bank_tran_no", length = 100)
    private String bankTranNo;

    @Column(name = "card_type", length = 50)
    private String cardType;

    @Column(name = "transaction_no", length = 100)
    private String transactionNo;

    @Column(name = "response_code", length = 10)
    private String responseCode;

    @Column(name = "transaction_status", length = 10)
    private String transactionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "pay_date", length = 20)
    private String payDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = PaymentStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
