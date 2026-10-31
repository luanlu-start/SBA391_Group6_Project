package com.fptpost.modules.pricing.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Append-only application history. The applying owner must lock the promotion before inserting a usage. */
@Entity @Immutable
@Table(name = "promotion_usages", uniqueConstraints = @UniqueConstraint(name = "uq_promotion_usage_order", columnNames = "order_id"),
        indexes = @Index(name = "ix_promotion_usages_promo", columnList = "promo_id"))
@Getter @Builder @NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
public class PromotionUsage {
    @Id @Column(nullable = false, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promo_id", nullable = false, updatable = false)
    private Promotion promotion;
    @Column(name = "user_public_id", nullable = false, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID userPublicId;
    // Order currently exposes a Long SQL ID, not a public UUID. Keep the scalar owner contract.
    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;
}
