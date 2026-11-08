package com.fptpost.modules.relay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoverTokenResponse {
    private Long orderId;
    private String orderCode;
    private Integer legNo;
    private String tokenCode;
    private String qrData;
    private Long generatedByCourierId;
    private String generatedByCourierName;
    private Instant expiresAt;
    private Long remainingSeconds;
    private String meetingPointName;
    private String meetingPointAddress;
}
