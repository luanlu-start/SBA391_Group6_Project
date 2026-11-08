package com.fptpost.modules.relay.model;

import com.fptpost.modules.order.model.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Hồ sơ ghi nhận bằng chứng bàn giao chặng thành công giữa 2 người giao (DB-015, BR-F-060).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"order", "leg"})
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "handover_records", indexes = {
    @Index(name = "idx_handover_records_order_id", columnList = "order_id"),
    @Index(name = "idx_handover_records_leg_id", columnList = "leg_id")
})
public class HandoverRecord {

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

    @Column(name = "from_courier_id", nullable = false)
    private Long fromCourierId;

    @Nationalized
    @Column(name = "from_courier_name", length = 150, columnDefinition = "nvarchar(150)")
    private String fromCourierName;

    @Column(name = "to_courier_id", nullable = false)
    private Long toCourierId;

    @Nationalized
    @Column(name = "to_courier_name", length = 150, columnDefinition = "nvarchar(150)")
    private String toCourierName;

    @Column(name = "token_code", nullable = false, length = 64)
    private String tokenCode;

    @Column(name = "handover_photo_url", length = 500)
    private String handoverPhotoUrl;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Nationalized
    @Column(name = "meeting_point_name", length = 200, columnDefinition = "nvarchar(200)")
    private String meetingPointName;

    @Nationalized
    @Column(name = "notes", length = 500, columnDefinition = "nvarchar(500)")
    private String notes;

    @Column(name = "handover_time", nullable = false)
    private Instant handoverTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.handoverTime == null) {
            this.handoverTime = now;
        }
    }
}
