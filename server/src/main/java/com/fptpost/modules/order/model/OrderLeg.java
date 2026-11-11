package com.fptpost.modules.order.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "order_legs", indexes = {
    @Index(name = "idx_order_legs_order_id", columnList = "order_id"),
    @Index(name = "idx_order_legs_courier_id", columnList = "courier_id"),
    @Index(name = "idx_order_legs_status", columnList = "status")
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

    @Nationalized
    @Column(name = "pickup_address", nullable = false, length = 500, columnDefinition = "nvarchar(500)")
    private String pickupAddress;

    @Column(name = "pickup_latitude", precision = 10, scale = 7)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", precision = 10, scale = 7)
    private BigDecimal pickupLongitude;

    @Nationalized
    @Column(name = "delivery_address", nullable = false, length = 500, columnDefinition = "nvarchar(500)")
    private String deliveryAddress;

    @Column(name = "delivery_latitude", precision = 10, scale = 7)
    private BigDecimal deliveryLatitude;

    @Column(name = "delivery_longitude", precision = 10, scale = 7)
    private BigDecimal deliveryLongitude;

    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "leg_fee", precision = 15, scale = 2)
    private BigDecimal legFee;

    @Column(name = "courier_id")
    private Long courierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private OrderStatus status = OrderStatus.FINDING_SHIPPER;

    @Column(name = "handover_photo_url", length = 1000)
    private String handoverPhotoUrl;

    @Nationalized
    @Column(name = "handover_note", length = 500, columnDefinition = "nvarchar(500)")
    private String handoverNote;

    @Column(name = "handover_at")
    private Instant handoverAt;

    @Nationalized
    @Column(name = "handover_meeting_point", length = 255, columnDefinition = "nvarchar(255)")
    private String handoverMeetingPoint;

    @Column(name = "handover_latitude", precision = 10, scale = 7)
    private BigDecimal handoverLatitude;

    @Column(name = "handover_longitude", precision = 10, scale = 7)
    private BigDecimal handoverLongitude;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = OrderStatus.FINDING_SHIPPER;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
