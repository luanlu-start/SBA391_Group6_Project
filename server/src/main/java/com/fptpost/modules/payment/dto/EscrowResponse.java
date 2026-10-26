package com.fptpost.modules.payment.dto;

import com.fptpost.modules.payment.model.EscrowPaymentMethod;
import com.fptpost.modules.payment.model.EscrowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * DTO phản hồi thông tin ký quỹ đơn hàng (UC-36, API-092).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EscrowResponse {

    private Long id;
    private Long orderId;
    private String orderCode;
    private Long senderId;
    private Long courierId;
    private Long amount;
    private EscrowStatus status;
    private EscrowPaymentMethod paymentMethod;
    private Instant releaseAt;
    private boolean frozen;
    private Instant createdAt;
    private Instant updatedAt;
}
