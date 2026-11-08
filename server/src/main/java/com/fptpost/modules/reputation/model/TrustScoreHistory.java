package com.fptpost.modules.reputation.model;

import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.reputation.enums.TrustScoreReason;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Mỗi lần điểm hoặc hạng thay đổi (DB-038: trust_score_history). */
@Entity
@Table(name = "trust_score_history", indexes = @Index(name = "idx_trust_history_courier", columnList = "courier_id, calculated_at"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustScoreHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "courier_id", nullable = false, updatable = false)
    private Long courierId;

    @Column(nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal score;

    @Column(name = "previous_score", precision = 5, scale = 2, updatable = false)
    private BigDecimal previousScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private CourierTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_tier", length = 20, updatable = false)
    private CourierTier previousTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TrustScoreReason reason;

    @Column(length = 300, columnDefinition = "nvarchar(300)", updatable = false)
    private String note;

    @Column(name = "calculated_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant calculatedAt;
}
