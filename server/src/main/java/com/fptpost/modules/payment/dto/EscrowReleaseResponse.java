package com.fptpost.modules.payment.dto;

import com.fptpost.modules.payment.model.EscrowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * DTO phản hồi kết quả giải ngân tiền ký quỹ cho người giao (UC-36, API-093).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EscrowReleaseResponse {

    private Long orderId;
    private String orderCode;
    private Long courierId;
    private Long amountReleased;
    private EscrowStatus status;
    private String message;
    private Instant releasedAt;
}
