package com.fptpost.modules.identity.model;

import com.fptpost.modules.identity.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity @Table(name = "courier_profiles") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CourierProfile {
    @Id @Column(name = "user_id") private Long userId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Enumerated(EnumType.STRING) @Column(name = "vehicle_type", nullable = false, length = 20) private VehicleType vehicleType;
    @ElementCollection @CollectionTable(name = "courier_areas", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "position") @Column(name = "area", nullable = false, columnDefinition = "nvarchar(80)") private List<String> areas;
    @ElementCollection @CollectionTable(name = "courier_available_hours", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "position") private List<AvailabilityWindow> availableHours;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CourierTier tier;
    @Column(name = "trust_score", nullable = false, precision = 5, scale = 2) private BigDecimal trustScore;
    @Column(name = "completed_orders", nullable = false) private int completedOrders;
    @Column(name = "restricted_until", columnDefinition = "datetime2(6)") private Instant restrictedUntil;
    @Column(name = "commitment_version", nullable = false, length = 50) private String commitmentVersion;
    @Column(name = "registered_at", nullable = false, columnDefinition = "datetime2(6)") private Instant registeredAt;
    @Version private long version;
}
