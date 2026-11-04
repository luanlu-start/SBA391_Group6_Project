package com.fptpost.modules.order.dto.response;

import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.enums.VehicleType;
import com.fptpost.modules.order.model.DeliveryUrgency;
import com.fptpost.modules.order.model.InvitationStatus;
import com.fptpost.modules.order.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationResponse {
    private Long id;
    private Long orderId;
    private String orderCode;
    private Long senderId;
    private String senderName;
    private String senderPhone;

    private Long courierId;
    private String courierName;
    private String courierPhone;
    private String courierEmail;
    private CourierTier courierTier;
    private BigDecimal courierTrustScore;
    private Integer courierCompletedOrders;
    private VehicleType courierVehicleType;

    private String productName;
    private String productDescription;
    private BigDecimal weightKg;
    private String pickupAddress;
    private String deliveryAddress;
    private BigDecimal distanceKm;
    private BigDecimal declaredValue;
    private BigDecimal priceSuggested;
    private BigDecimal priceAgreed;
    private DeliveryUrgency urgency;
    private OrderStatus orderStatus;

    private String message;
    private InvitationStatus status;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
}
