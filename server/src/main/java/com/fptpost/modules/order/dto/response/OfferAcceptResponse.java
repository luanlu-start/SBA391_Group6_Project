package com.fptpost.modules.order.dto.response;

import com.fptpost.modules.order.model.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferAcceptResponse {
    private Long offerId;
    private Long orderId;
    private String orderCode;
    private Long courierId;
    private String courierName;
    private BigDecimal priceAgreed;
    private OrderStatus orderStatus;
    private Instant holdExpiresAt;
    private String message;
}
