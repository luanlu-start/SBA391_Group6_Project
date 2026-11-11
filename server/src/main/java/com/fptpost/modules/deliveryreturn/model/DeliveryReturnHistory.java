package com.fptpost.modules.deliveryreturn.model;

import com.fptpost.modules.order.model.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;
import java.time.Instant;

@Entity @Table(name = "delivery_return_history", indexes = @Index(name = "idx_return_history_order", columnList = "order_id,id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DeliveryReturnHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "return_case_id") private Long returnCaseId;
    @Column(name = "actor_id", nullable = false) private Long actorId;
    @Column(nullable = false, length = 40) private String action;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", nullable = false, length = 50) private OrderStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 50) private OrderStatus toStatus;
    @Nationalized @Column(nullable = false, length = 1000) private String note;
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(6)") private Instant createdAt;
}
