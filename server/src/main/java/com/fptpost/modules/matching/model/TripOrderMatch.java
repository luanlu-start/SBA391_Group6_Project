package com.fptpost.modules.matching.model;

import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.trip.model.Trip;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Thực thể lưu trữ kết quả gợi ý ghép đơn - chuyến (DB-021: trip_order_matches).
 */
@Entity
@Table(name = "trip_order_matches", indexes = {
        @Index(name = "idx_match_trip_id", columnList = "trip_id"),
        @Index(name = "idx_match_order_id", columnList = "order_id"),
        @Index(name = "idx_match_status", columnList = "status"),
        @Index(name = "idx_match_score", columnList = "score")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripOrderMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "detour_km", nullable = false)
    private Double detourKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TripOrderMatchStatus status = TripOrderMatchStatus.SUGGESTED;

    @Column(name = "match_reasons", columnDefinition = "nvarchar(500)")
    private String matchReasons;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "datetime2(6)")
    private Instant updatedAt;
}
