package com.fptpost.modules.relay.model;

import com.fptpost.modules.order.model.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * Báo cáo sự cố khi bàn giao chặng (Luồng ngoại lệ A2, UC-33).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"order", "leg"})
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "handover_incidents", indexes = {
    @Index(name = "idx_handover_incidents_order_id", columnList = "order_id"),
    @Index(name = "idx_handover_incidents_leg_id", columnList = "leg_id")
})
public class HandoverIncident {

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

    @Column(name = "reporter_courier_id", nullable = false)
    private Long reporterCourierId;

    @Nationalized
    @Column(name = "reporter_name", length = 150, columnDefinition = "nvarchar(150)")
    private String reporterName;

    @Nationalized
    @Column(name = "incident_type", nullable = false, length = 100, columnDefinition = "nvarchar(100)")
    private String incidentType;

    @Nationalized
    @Column(name = "description", nullable = false, length = 1000, columnDefinition = "nvarchar(1000)")
    private String description;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @PrePersist
    protected void onCreate() {
        if (this.reportedAt == null) {
            this.reportedAt = Instant.now();
        }
    }
}
