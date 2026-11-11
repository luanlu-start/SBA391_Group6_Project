package com.fptpost.modules.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoverQrResponse {
    private String qrToken;
    private String otpCode;
    private Long orderId;
    private String orderCode;
    private Integer legNo;
    private Integer nextLegNo;
    private Long fromCourierId;
    private Long toCourierId;
    private String meetingPoint;
    private Instant issuedAt;
    private Instant expiresAt;
    private long remainingSeconds;
    private String handoverUrl;
}
