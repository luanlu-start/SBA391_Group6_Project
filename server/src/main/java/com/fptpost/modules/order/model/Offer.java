package com.fptpost.modules.order.model;

import com.fptpost.modules.identity.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DB-017: offers - Chào giá (Sàn đơn & Ghép đơn - MKT)
 * Phục vụ UC-18 (Gửi chào giá), UC-19 (Xử lý chào giá: chấp nhận, từ chối, phản giá).
 */
@Entity
@Table(name = "offers", indexes = {
    @Index(name = "idx_offers_order_id_status", columnList = "order_id, status"),
    @Index(name = "idx_offers_courier_id_status", columnList = "courier_id, status"),
    @Index(name = "idx_offers_order_courier", columnList = "order_id, courier_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id", nullable = false)
    private User courier;

    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Nationalized
    @Column(name = "message", length = 500, columnDefinition = "nvarchar(500)")
    private String message;

    @Column(name = "eta_pickup")
    private Instant etaPickup;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OfferStatus status;

    @Column(name = "parent_offer_id")
    private Long parentOfferId;

    @Column(name = "round_no", nullable = false)
    @Builder.Default
    private Integer roundNo = 1;

    @Column(name = "created_by_sender", nullable = false)
    @Builder.Default
    private boolean createdBySender = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

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
            this.status = OfferStatus.PENDING;
        }
        if (this.expiresAt == null) {
            this.expiresAt = now.plusSeconds(120 * 60); // 120 phút (PRM-041)
        }
        if (this.roundNo == null) {
            this.roundNo = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
