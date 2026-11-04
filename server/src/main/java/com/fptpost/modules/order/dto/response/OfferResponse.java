package com.fptpost.modules.order.dto.response;

import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.order.model.OfferStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferResponse {
    private Long id;
    private Long orderId;
    private String orderCode;
    private Long courierId;
    private String courierName;
    private String courierAvatarUrl;
    private String courierPhone;
    private CourierTier courierTier;
    private String courierTierName;
    private Integer courierTrustScore;
    private Integer courierCompletedOrders;
    private boolean fptBadge;
    private BigDecimal price;
    private BigDecimal priceSuggested;
    private Double priceDifferencePercentage;
    private String message;
    private Instant etaPickup;
    private OfferStatus status;
    private Long parentOfferId;
    private Integer roundNo;
    private boolean createdBySender;
    private Instant expiresAt;
    private boolean expired;
    private Instant createdAt;
    private Instant updatedAt;
}
