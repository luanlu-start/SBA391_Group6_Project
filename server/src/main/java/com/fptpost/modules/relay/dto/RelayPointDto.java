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
public class RelayPointDto {
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String name;
    private Long meetingPointId;
}
