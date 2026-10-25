package com.fptpost.modules.identity.model;

import com.fptpost.modules.identity.enums.CourierTier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * DB-010: courier_profiles - Hồ sơ người giao.
 */
@Entity
@Table(name = "courier_profiles", indexes = {
    @Index(name = "idx_courier_profiles_user_id", columnList = "user_id", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    @Builder.Default
    private CourierTier tier = CourierTier.NEW;

    @Column(name = "trust_score", nullable = false)
    @Builder.Default
    private Integer trustScore = 50;

    @Column(name = "completed_orders", nullable = false)
    @Builder.Default
    private Integer completedOrders = 0;

    @Nationalized
    @Column(name = "vehicle_type", length = 50, columnDefinition = "nvarchar(50)")
    private String vehicleType;

    @Nationalized
    @Column(name = "areas", length = 255, columnDefinition = "nvarchar(255)")
    private String areas;

    @Column(name = "restricted_until", columnDefinition = "datetime2(6)")
    private Instant restrictedUntil;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (tier == null) tier = CourierTier.NEW;
        if (trustScore == null) trustScore = 50;
        if (completedOrders == null) completedOrders = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
