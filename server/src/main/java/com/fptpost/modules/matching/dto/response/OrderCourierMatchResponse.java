package com.fptpost.modules.matching.dto.response;

import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.enums.VehicleType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chuyến đi và người giao tiện đường gợi ý cho đơn hàng (API-054)")
public class OrderCourierMatchResponse {

    private Long tripId;
    private Long courierId;
    private UUID courierPublicId;
    private String courierName;
    private String courierAvatar;
    private String courierPhone;
    private CourierTier courierTier;
    private BigDecimal trustScore;
    private int completedOrders;
    private VehicleType vehicleType;
    private String vehicleLabel;

    // Trip details
    private String origin;
    private Double originLat;
    private Double originLng;
    private String destination;
    private Double destinationLat;
    private Double destinationLng;
    private Instant departAt;
    private Double distanceKm;
    private Double capacityKg;
    private BigDecimal floorPrice;
    private String routePolyline;

    // Matching metrics (UC-21)
    private Double detourKm;
    private Double matchScore; // thang điểm 0 - 100
    private List<String> matchReasons;
}
