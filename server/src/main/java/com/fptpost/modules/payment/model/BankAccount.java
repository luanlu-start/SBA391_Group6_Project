package com.fptpost.modules.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Thực thể Tài khoản ngân hàng nhận tiền của người dùng/người giao (UC-39, DB-011).
 * Số tài khoản được mã hóa AES-256-GCM khi lưu trữ (BR-N-026),
 * kèm thời gian chờ bảo mật 24 giờ trước khi rút tiền (BR-R-058).
 */
@Entity
@Table(name = "bank_accounts", indexes = {
        @Index(name = "idx_bank_accounts_user_id", columnList = "user_id"),
        @Index(name = "idx_bank_accounts_user_hash", columnList = "user_id, bank_code, account_no_hash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "bank_code", nullable = false, length = 30)
    private String bankCode;

    @Column(name = "bank_name", nullable = false, columnDefinition = "NVARCHAR(100)")
    private String bankName;

    @Column(name = "account_no_enc", nullable = false, length = 1000)
    private String accountNoEnc;

    @Column(name = "account_no_hash", nullable = false, length = 64)
    private String accountNoHash;

    @Column(name = "account_last4", nullable = false, length = 4)
    private String accountLast4;

    @Column(name = "holder_name", nullable = false, columnDefinition = "NVARCHAR(100)")
    private String holderName;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "cooldown_until", nullable = false)
    private Instant cooldownUntil;

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
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
