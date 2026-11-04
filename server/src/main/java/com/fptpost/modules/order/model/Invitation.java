package com.fptpost.modules.order.model;

import com.fptpost.modules.identity.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * DB-018: invitations - Lời mời trực tiếp người giao
 * Phục vụ UC-20 (Mời trực tiếp người giao / API-051)
 */
@Entity
@Table(name = "invitations", indexes = {
    @Index(name = "idx_invitations_order_id_status", columnList = "order_id, status"),
    @Index(name = "idx_invitations_courier_id_status", columnList = "courier_id, status"),
    @Index(name = "idx_invitations_sender_id", columnList = "sender_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id", nullable = false)
    private User courier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InvitationStatus status;

    @Nationalized
    @Column(name = "message", length = 500, columnDefinition = "nvarchar(500)")
    private String message;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = InvitationStatus.PENDING;
        }
        if (this.expiresAt == null) {
            this.expiresAt = now.plusSeconds(24 * 3600); // Mặc định hiệu lực 24 giờ
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
