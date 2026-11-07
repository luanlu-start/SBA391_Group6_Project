package com.fptpost.modules.batch.model;

import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.trip.model.Trip;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "delivery_batches", indexes = {
    @Index(name = "idx_batch_code", columnList = "batch_code", unique = true),
    @Index(name = "idx_batch_courier_id", columnList = "courier_id"),
    @Index(name = "idx_batch_status", columnList = "status"),
    @Index(name = "idx_batch_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_code", nullable = false, unique = true, length = 50)
    private String batchCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id", nullable = false)
    private User courier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Nationalized
    @Column(name = "batch_name", length = 255, columnDefinition = "nvarchar(255)")
    private String batchName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private DeliveryBatchStatus status = DeliveryBatchStatus.PLANNING;

    @Column(name = "total_orders", nullable = false)
    private Integer totalOrders;

    @Column(name = "total_weight_kg", precision = 10, scale = 3)
    private BigDecimal totalWeightKg;

    @Column(name = "total_distance_km", precision = 10, scale = 2)
    private BigDecimal totalDistanceKm;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Nationalized
    @Column(name = "notes", length = 1000, columnDefinition = "nvarchar(1000)")
    private String notes;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stopOrder ASC")
    @Builder.Default
    private List<DeliveryBatchStop> stops = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "delivery_batch_orders",
        joinColumns = @JoinColumn(name = "batch_id"),
        inverseJoinColumns = @JoinColumn(name = "order_id")
    )
    @Builder.Default
    private Set<Order> orders = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "datetime2(6)")
    private Instant updatedAt;

    public void addStop(DeliveryBatchStop stop) {
        stops.add(stop);
        stop.setBatch(this);
    }
}
