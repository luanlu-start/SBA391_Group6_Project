package com.fptpost.modules.relay.model;

import com.fptpost.modules.order.model.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Chặng trong đơn giao hàng tiếp sức (DB-022, BR-F-059, BR-F-060, BR-R-048).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "order")
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "order_legs", indexes = {
    @Index(name = "idx_order_legs_order_id", columnList = "order_id"),
    @Index(name = "idx_order_legs_courier_id", columnList = "courier_id"),
    @Index(name = "idx_order_legs_status", columnList = "status"),
    @Index(name = "idx_order_legs_order_leg_no", columnList = "order_id, leg_no", unique = true)
})
public class OrderLeg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "leg_no", nullable = false)
    private Integer legNo;

    @Column(name = "courier_id")
    private Long courierId;

    @Nationalized
    @Column(name = "courier_name", length = 150, columnDefinition = "nvarchar(150)")
    private String courierName;

    @Column(name = "courier_phone", length = 20)
    private String courierPhone;

    @Nationalized
    @Column(name = "start_address", nullable = false, length = 500, columnDefinition = "nvarchar(500)")
    private String startAddress;

    @Column(name = "start_latitude", precision = 10, scale = 7)
    private BigDecimal startLatitude;

    @Column(name = "start_longitude", precision = 10, scale = 7)
    private BigDecimal startLongitude;

    @Nationalized
    @Column(name = "end_address", nullable = false, length = 500, columnDefinition = "nvarchar(500)")
    private String endAddress;

    @Column(name = "end_latitude", precision = 10, scale = 7)
    private BigDecimal endLatitude;

    @Column(name = "end_longitude", precision = 10, scale = 7)
    private BigDecimal endLongitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_point_id")
    private MeetingPoint meetingPoint;

    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "fee", precision = 15, scale = 2)
    private BigDecimal fee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private LegStatus status = LegStatus.PENDING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "handed_over_at")
    private Instant handedOverAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Nationalized
    @Column(name = "handover_note", length = 500, columnDefinition = "nvarchar(500)")
    private String handoverNote;

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
            this.status = LegStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
