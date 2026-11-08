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
public class HandoverIncidentResponse {
    private Long id;
    private Long orderId;
    private Integer legNo;
    private String incidentType;
    private String description;
    private String photoUrl;
    private Instant reportedAt;
    private String message;
}
