package com.fptpost.modules.payment.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity ví người dùng (DB-024: wallets).
 * Quản lý số dư khả dụng, tiền ký quỹ đang giữ và tiền chờ rút.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "wallets", indexes = {
        @Index(name = "idx_wallets_user_id", columnList = "user_id", unique = true),
        @Index(name = "idx_wallets_updated_at", columnList = "updated_at")
})
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * Số dư khả dụng (VND) - dùng để chi tiêu, ký quỹ hoặc rút tiền.
     */
    @Builder.Default
    @Column(name = "available", nullable = false)
    private Long available = 0L;

    /**
     * Tiền đang bị giam giữ ký quỹ theo các đơn hàng đang giao (VND).
     */
    @Builder.Default
    @Column(name = "escrow_held", nullable = false)
    private Long escrowHeld = 0L;

    /**
     * Tiền đang trong quy trình chờ duyệt/rút về ngân hàng (VND).
     */
    @Builder.Default
    @Column(name = "pending_withdraw", nullable = false)
    private Long pendingWithdraw = 0L;

    /**
     * Khóa lạc quan chống xung đột cập nhật đồng thời (Optimistic Locking).
     */
    @Version
    private long version;

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
        if (this.available == null) {
            this.available = 0L;
        }
        if (this.escrowHeld == null) {
            this.escrowHeld = 0L;
        }
        if (this.pendingWithdraw == null) {
            this.pendingWithdraw = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
