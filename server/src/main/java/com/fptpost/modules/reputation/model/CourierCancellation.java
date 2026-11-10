package com.fptpost.modules.reputation.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Lần người giao hủy đơn sau khi đã nhận (BR-R-075); ghi qua {@code TrustScoreService.recordCourierCancellation}. */
@Entity
@Table(name = "courier_cancellations",
        uniqueConstraints = @UniqueConstraint(name = "uq_courier_cancellation_order", columnNames = {"courier_id", "order_id"}),
        indexes = @Index(name = "idx_courier_cancellations_time", columnList = "courier_id, cancelled_at"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierCancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "courier_id", nullable = false, updatable = false)
    private Long courierId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "cancelled_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant cancelledAt;
}
