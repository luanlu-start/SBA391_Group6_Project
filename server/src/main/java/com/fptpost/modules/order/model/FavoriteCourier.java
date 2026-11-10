package com.fptpost.modules.order.model;

import com.fptpost.modules.identity.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * DB-019: favorite_couriers - Người giao yêu thích của người gửi
 * Phục vụ UC-20 (Mời trực tiếp & người giao yêu thích / API-052)
 */
@Entity
@Table(name = "favorite_couriers", uniqueConstraints = {
    @UniqueConstraint(name = "uk_favorite_sender_courier", columnNames = {"sender_id", "courier_id"})
}, indexes = {
    @Index(name = "idx_favorite_sender", columnList = "sender_id"),
    @Index(name = "idx_favorite_courier", columnList = "courier_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteCourier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id", nullable = false)
    private User courier;

    @Nationalized
    @Column(name = "note", length = 255, columnDefinition = "nvarchar(255)")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
