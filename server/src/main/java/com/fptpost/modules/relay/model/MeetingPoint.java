package com.fptpost.modules.relay.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Điểm hẹn bàn giao an toàn đã được kiểm duyệt và phê duyệt (DB-044, BR-F-061).
 * Các địa điểm công cộng như cổng trường, sảnh thư viện, quầy dịch vụ campus.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "meeting_points", indexes = {
    @Index(name = "idx_meeting_points_active", columnList = "is_active"),
    @Index(name = "idx_meeting_points_campus", columnList = "campus_area")
})
public class MeetingPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nationalized
    @Column(name = "name", nullable = false, length = 200, columnDefinition = "nvarchar(200)")
    private String name;

    @Nationalized
    @Column(name = "address", nullable = false, length = 500, columnDefinition = "nvarchar(500)")
    private String address;

    @Column(name = "latitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal longitude;

    @Nationalized
    @Column(name = "campus_area", length = 150, columnDefinition = "nvarchar(150)")
    private String campusArea;

    @Nationalized
    @Column(name = "description", length = 500, columnDefinition = "nvarchar(500)")
    private String description;

    @Column(name = "has_security_guard")
    @Builder.Default
    private Boolean hasSecurityGuard = true;

    @Column(name = "has_surveillance_camera")
    @Builder.Default
    private Boolean hasSurveillanceCamera = true;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

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
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
