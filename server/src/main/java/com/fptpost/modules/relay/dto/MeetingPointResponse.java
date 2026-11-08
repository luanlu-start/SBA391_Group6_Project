package com.fptpost.modules.relay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingPointResponse {
    private Long id;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String campusArea;
    private String description;
    private Boolean hasSecurityGuard;
    private Boolean hasSurveillanceCamera;
    private Boolean isActive;
}
