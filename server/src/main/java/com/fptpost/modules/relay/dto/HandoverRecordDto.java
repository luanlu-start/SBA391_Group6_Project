package com.fptpost.modules.relay.dto;

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
public class HandoverRecordDto {
    private Long id;
    private Integer legNo;
    private Long fromCourierId;
    private String fromCourierName;
    private Long toCourierId;
    private String toCourierName;
    private String handoverPhotoUrl;
    private String meetingPointName;
    private String notes;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Instant handoverTime;
}
