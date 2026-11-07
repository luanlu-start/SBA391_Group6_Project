package com.fptpost.modules.batch.model;

import com.fptpost.modules.order.model.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "delivery_batch_stops", indexes = {
    @Index(name = "idx_batch_stop_batch_id", columnList = "batch_id"),
    @Index(name = "idx_batch_stop_order_id", columnList = "order_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryBatchStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private DeliveryBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", nullable = false, length = 20)
    private BatchStopType stopType;

    @Nationalized
    @Column(name = "address", nullable = false, length = 500, columnDefinition = "nvarchar(500)")
    private String address;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Nationalized
    @Column(name = "contact_name", length = 100, columnDefinition = "nvarchar(100)")
    private String contactName;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Nationalized
    @Column(name = "item_description", length = 255, columnDefinition = "nvarchar(255)")
    private String itemDescription;

    @Column(name = "weight_kg", precision = 10, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "distance_from_prev_km", precision = 10, scale = 2)
    private BigDecimal distanceFromPrevKm;

    @Column(name = "estimated_arrival_minutes")
    private Integer estimatedArrivalMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BatchStopStatus status = BatchStopStatus.PENDING;

    @Column(name = "completed_at", columnDefinition = "datetime2(6)")
    private Instant completedAt;
}
