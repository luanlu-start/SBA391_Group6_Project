package com.fptpost.modules.payment.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity lưu khoản ký quỹ theo đơn hàng (DB-028: escrows).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "escrows", indexes = {
        @Index(name = "idx_escrows_order_id", columnList = "order_id", unique = true),
        @Index(name = "idx_escrows_sender_id", columnList = "sender_id"),
        @Index(name = "idx_escrows_status", columnList = "status")
})
public class Escrow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EscrowStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private EscrowPaymentMethod paymentMethod;

    @Column(name = "release_at")
    private Instant releaseAt;

    @Builder.Default
    @Column(name = "frozen", nullable = false)
    private boolean frozen = false;

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
            this.status = EscrowStatus.HELD;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
