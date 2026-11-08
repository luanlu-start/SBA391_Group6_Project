package com.fptpost.modules.relay.model;

import com.fptpost.modules.order.model.Order;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Mã QR / Token xác thực bàn giao giữa các chặng (DB-023, PRM-097, BR-F-060, BR-R-048).
 * Thời hạn hiệu lực mặc định 10 phút.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"order", "leg"})
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "handover_tokens", indexes = {
    @Index(name = "idx_handover_tokens_code", columnList = "token_code"),
    @Index(name = "idx_handover_tokens_order_leg", columnList = "order_id, leg_no"),
    @Index(name = "idx_handover_tokens_expiry", columnList = "expires_at, is_used")
})
public class HandoverToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leg_id", nullable = false)
    private OrderLeg leg;

    @Column(name = "leg_no", nullable = false)
    private Integer legNo;

    @Column(name = "token_code", nullable = false, length = 64)
    private String tokenCode;

    @Column(name = "purpose", nullable = false, length = 30)
    @Builder.Default
    private String purpose = "HANDOVER";

    @Column(name = "generated_by_courier_id", nullable = false)
    private Long generatedByCourierId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "used_by_courier_id")
    private Long usedByCourierId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.isUsed == null) {
            this.isUsed = false;
        }
        if (this.attempts == null) {
            this.attempts = 0;
        }
        if (this.purpose == null) {
            this.purpose = "HANDOVER";
        }
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    public boolean isValid() {
        return Boolean.FALSE.equals(isUsed) && !isExpired();
    }
}
