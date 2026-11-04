package com.fptpost.modules.identity.dto.response;

import com.fptpost.modules.identity.enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CourierProfileResponse {
    private UUID userId;
    private VehicleType vehicleType;
    private List<String> areas;
    private List<AvailabilityWindowResponse> availableHours;
    private CourierTier tier;
    private BigDecimal trustScore;
    private int completedOrders;
    private Instant restrictedUntil;
    private Instant registeredAt;
}
