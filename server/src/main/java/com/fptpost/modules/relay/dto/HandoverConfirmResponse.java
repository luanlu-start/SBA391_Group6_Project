package com.fptpost.modules.relay.dto;

import com.fptpost.modules.relay.model.LegStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoverConfirmResponse {
    private Boolean success;
    private String message;
    private Long orderId;
    private String orderCode;
    private Integer completedLegNo;
    private LegStatus completedLegStatus;
    private Integer nextLegNo;
    private LegStatus nextLegStatus;
    private Long nextCourierId;
    private String nextCourierName;
    private String nextDestinationAddress;
    private Instant handoverTime;
    private String proofPhotoUrl;
}
