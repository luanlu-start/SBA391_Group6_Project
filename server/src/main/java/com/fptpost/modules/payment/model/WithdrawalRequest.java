package com.fptpost.modules.payment.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity yêu cầu rút tiền từ ví về tài khoản ngân hàng (UC-40, UC-41).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "withdrawal_requests", indexes = {
        @Index(name = "idx_wdr_request_code", columnList = "request_code", unique = true),
        @Index(name = "idx_wdr_user_id", columnList = "user_id"),
        @Index(name = "idx_wdr_status", columnList = "status"),
        @Index(name = "idx_wdr_created_at", columnList = "created_at")
})
public class WithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_code", nullable = false, unique = true, length = 100)
    private String requestCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Builder.Default
    @Column(name = "fee_amount", nullable = false)
    private Long feeAmount = 0L;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "account_holder_name", nullable = false, length = 100)
    private String accountHolderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WithdrawalStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

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
            this.status = WithdrawalStatus.PENDING_REVIEW;
        }
        if (this.feeAmount == null) {
            this.feeAmount = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
