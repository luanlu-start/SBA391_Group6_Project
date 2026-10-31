package com.fptpost.modules.catalog.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item_categories", indexes = {
    @Index(name = "idx_item_categories_code", columnList = "code", unique = true),
    @Index(name = "idx_item_categories_active", columnList = "active")
})
public class ItemCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Nationalized
    @Column(nullable = false, length = 100, columnDefinition = "nvarchar(100)")
    private String name;

    @Nationalized
    @Column(length = 500, columnDefinition = "nvarchar(500)")
    private String description;

    @Builder.Default
    @Column(name = "is_prohibited", nullable = false)
    private Boolean isProhibited = false;

    @Builder.Default
    @Column(name = "requires_review", nullable = false)
    private Boolean requiresReview = false;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (isProhibited == null) {
            isProhibited = false;
        }
        if (requiresReview == null) {
            requiresReview = false;
        }
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
