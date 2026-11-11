package com.fptpost.modules.deliveryreturn.model;

import com.fptpost.modules.order.model.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "delivery_return_cases", indexes = @Index(name = "idx_return_order", columnList = "order_id,id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DeliveryReturnCase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false) private Order order;
    @Column(name = "courier_id", nullable = false) private Long courierId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private FailureReason reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReturnState state;
    @Nationalized @Column(nullable = false, length = 1000) private String description;
    @Column(name = "evidence_key", nullable = false, columnDefinition = "uniqueidentifier") private UUID evidenceKey;
    @Column(name = "evidence_media_type", nullable = false, length = 30) private String evidenceMediaType;
    @Column(name = "delivery_fee", nullable = false, precision = 15, scale = 0) private BigDecimal deliveryFee;
    @Column(name = "return_fee", nullable = false, precision = 15, scale = 0) private BigDecimal returnFee;
    @Column(name = "return_rate", nullable = false, precision = 10, scale = 6) private BigDecimal returnRate;
    @Column(name = "fee_policy_version", nullable = false) private Long feePolicyVersion;
    @Column(name = "required_calls", nullable = false) private int requiredCalls;
    @Column(name = "contact_window_minutes", nullable = false) private int contactWindowMinutes;
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(6)") private Instant createdAt;
    @Column(name = "resolved_at", columnDefinition = "datetime2(6)") private Instant resolvedAt;
    // Only a salted hash is persisted. Codes never appear in history, logs or ordinary responses.
    @Column(name = "code_hash", length = 64) private String codeHash;
    @Column(name = "code_salt", length = 36) private String codeSalt;
    @Column(name = "code_expires_at", columnDefinition = "datetime2(6)") private Instant codeExpiresAt;
    @Column(name = "code_issued_at", columnDefinition = "datetime2(6)") private Instant codeIssuedAt;
    @Column(name = "code_failures", nullable = false) private int codeFailures;
}
