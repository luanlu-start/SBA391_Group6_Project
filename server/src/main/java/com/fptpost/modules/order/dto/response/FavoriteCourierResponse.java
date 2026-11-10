package com.fptpost.modules.order.dto.response;

import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteCourierResponse {
    private Long id;
    private Long courierId;
    private String fullName;
    private String phone;
    private String email;
    private VehicleType vehicleType;
    private CourierTier tier;
    private BigDecimal trustScore;
    private int completedOrders;
    private List<String> areas;
    private String note;
    private Instant createdAt;
}
