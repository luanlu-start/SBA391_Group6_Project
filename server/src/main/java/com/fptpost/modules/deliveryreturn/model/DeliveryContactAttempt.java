package com.fptpost.modules.deliveryreturn.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "delivery_contact_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uq_return_contact_request", columnNames = {"order_id", "request_id"}),
        indexes = @Index(name = "idx_return_contact_window", columnList = "order_id,courier_id,created_at"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DeliveryContactAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "courier_id", nullable = false) private Long courierId;
    @Column(name = "request_id", nullable = false, columnDefinition = "uniqueidentifier") private UUID requestId;
    @Nationalized @Column(nullable = false, length = 500) private String note;
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(6)") private Instant createdAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "return_case_id") private DeliveryReturnCase returnCase;
}
