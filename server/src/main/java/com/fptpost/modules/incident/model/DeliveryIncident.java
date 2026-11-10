package com.fptpost.modules.incident.model;

import com.fptpost.modules.order.model.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_incidents", indexes = @Index(name = "idx_incidents_order_time", columnList = "order_id,created_at"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DeliveryIncident {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private IncidentType type;

    @Nationalized @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "evidence_key", columnDefinition = "uniqueidentifier")
    private UUID evidenceKey;

    @Column(name = "evidence_media_type", length = 30)
    private String evidenceMediaType;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;
}
