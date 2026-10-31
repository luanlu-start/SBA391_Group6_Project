package com.fptpost.modules.trip.model;

import com.fptpost.modules.identity.enums.VehicleType;
import com.fptpost.modules.identity.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trips")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id", nullable = false)
    private User courier;

    @Column(name = "origin", nullable = false, columnDefinition = "nvarchar(255)")
    private String origin;

    @Column(name = "origin_lat", nullable = false)
    private Double originLat;

    @Column(name = "origin_lng", nullable = false)
    private Double originLng;

    @Column(name = "destination", nullable = false, columnDefinition = "nvarchar(255)")
    private String destination;

    @Column(name = "destination_lat", nullable = false)
    private Double destinationLat;

    @Column(name = "destination_lng", nullable = false)
    private Double destinationLng;

    @Column(name = "route_polyline", columnDefinition = "nvarchar(max)")
    private String routePolyline;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "depart_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant departAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "capacity_kg", nullable = false)
    private Double capacityKg;

    @Column(name = "floor_price", precision = 12, scale = 2)
    private BigDecimal floorPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence", nullable = false, length = 20)
    @Builder.Default
    private TripRecurrence recurrence = TripRecurrence.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TripStatus status = TripStatus.SCHEDULED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "datetime2(6)")
    private Instant updatedAt;
}
