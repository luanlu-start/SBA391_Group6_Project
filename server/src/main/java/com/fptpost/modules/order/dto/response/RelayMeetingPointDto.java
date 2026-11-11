package com.fptpost.modules.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * BR-F-061: Điểm hẹn bàn giao an toàn đã được kiểm duyệt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelayMeetingPointDto {
    private String id;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String campus;
    private String description;
    private String securityFeatures;
}
