package com.fptpost.modules.pricing.model;

import com.fptpost.modules.pricing.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "promotions", uniqueConstraints = @UniqueConstraint(name = "uq_promotions_code", columnNames = "code"),
        indexes = @Index(name = "ix_promotions_created", columnList = "created_at, id"))
@Check(constraints = "discount_value > 0 AND usage_limit > 0 AND valid_to > valid_from "
        + "AND promotion_type IN ('FIXED_AMOUNT','PERCENTAGE') AND status IN ('ACTIVE','PAUSED') "
        + "AND target IN ('ALL','SELECTED_USERS')")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Promotion {
    @Id @Column(nullable = false, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID id;
    @Column(nullable = false, updatable = false, length = 50)
    private String code;
    @Enumerated(EnumType.STRING) @Column(name = "promotion_type", nullable = false, length = 20)
    private PromotionType type;
    @Column(name = "discount_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal value;
    @Column(name = "valid_from", nullable = false, columnDefinition = "datetime2(6)")
    private Instant validFrom;
    @Column(name = "valid_to", nullable = false, columnDefinition = "datetime2(6)")
    private Instant validTo;
    @Column(name = "usage_limit", nullable = false)
    private Integer usageLimit;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PromotionTarget target;
    @Builder.Default
    @ElementCollection @CollectionTable(name = "promotion_target_users", joinColumns = @JoinColumn(name = "promotion_id"),
            uniqueConstraints = @UniqueConstraint(name = "uq_promotion_target_user", columnNames = {"promotion_id", "user_public_id"}))
    @Column(name = "user_public_id", nullable = false, columnDefinition = "uniqueidentifier")
    @BatchSize(size = 100)
    private Set<UUID> targetUserIds = new HashSet<>();
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private PromotionStatus status;
    @Version @Column(nullable = false)
    private Long version;
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant updatedAt;
}
