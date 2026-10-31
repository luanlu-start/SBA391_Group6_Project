package com.fptpost.modules.trip.dto.response;

import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.enums.VehicleType;
import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.trip.model.Trip;
import com.fptpost.modules.trip.model.TripRecurrence;
import com.fptpost.modules.trip.model.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chuyến đi")
public class TripResponse {

    private Long id;
    private Long courierId;
    private UUID courierPublicId;
    private String courierName;
    private String courierAvatarUrl;
    private CourierTier courierTier;
    private BigDecimal courierTrustScore;

    private String origin;
    private Double originLat;
    private Double originLng;

    private String destination;
    private Double destinationLat;
    private Double destinationLng;

    private String routePolyline;
    private Double distanceKm;
    private Integer estimatedDurationMinutes;

    private Instant departAt;
    private VehicleType vehicleType;
    private Double capacityKg;
    private BigDecimal floorPrice;

    private TripRecurrence recurrence;
    private TripStatus status;

    private Integer ordersCount;
    private java.util.List<TripAttachedOrderResponse> attachedOrders;

    private Instant createdAt;
    private Instant updatedAt;

    public static TripResponse fromEntity(Trip trip) {
        return fromEntity(trip, null, null, null);
    }

    public static TripResponse fromEntity(Trip trip, UserProfile profile, CourierProfile courierProfile) {
        return fromEntity(trip, profile, courierProfile, null);
    }

    public static TripResponse fromEntity(Trip trip, UserProfile profile, CourierProfile courierProfile, java.util.List<TripAttachedOrderResponse> attachedOrders) {
        if (trip == null) return null;

        TripResponseBuilder builder = TripResponse.builder()
                .id(trip.getId())
                .origin(trip.getOrigin())
                .originLat(trip.getOriginLat())
                .originLng(trip.getOriginLng())
                .destination(trip.getDestination())
                .destinationLat(trip.getDestinationLat())
                .destinationLng(trip.getDestinationLng())
                .routePolyline(trip.getRoutePolyline())
                .distanceKm(trip.getDistanceKm())
                .estimatedDurationMinutes(trip.getEstimatedDurationMinutes())
                .departAt(trip.getDepartAt())
                .vehicleType(trip.getVehicleType())
                .capacityKg(trip.getCapacityKg())
                .floorPrice(trip.getFloorPrice())
                .recurrence(trip.getRecurrence())
                .status(trip.getStatus())
                .ordersCount(attachedOrders != null ? attachedOrders.size() : 0)
                .attachedOrders(attachedOrders)
                .createdAt(trip.getCreatedAt())
                .updatedAt(trip.getUpdatedAt());

        if (trip.getCourier() != null) {
            builder.courierId(trip.getCourier().getId());
            builder.courierPublicId(trip.getCourier().getPublicId());
        }

        if (profile != null) {
            builder.courierName(profile.getFullName());
            if (profile.getAvatarStorageKey() != null) {
                builder.courierAvatarUrl("/api/v1/files/" + profile.getAvatarStorageKey());
            }
        }

        if (courierProfile != null) {
            builder.courierTier(courierProfile.getTier());
            builder.courierTrustScore(courierProfile.getTrustScore());
        }

        return builder.build();
    }
}
