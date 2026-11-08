package com.fptpost.modules.relay.dto;

import com.fptpost.modules.relay.model.LegStatus;
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
public class OrderLegResponse {
    private Long id;
    private Long orderId;
    private String orderCode;
    private String productName;
    private Integer legNo;
    private Long courierId;
    private String courierName;
    private String courierPhone;
    private String startAddress;
    private BigDecimal startLatitude;
    private BigDecimal startLongitude;
    private String endAddress;
    private BigDecimal endLatitude;
    private BigDecimal endLongitude;
    private MeetingPointResponse meetingPoint;
    private BigDecimal distanceKm;
    private BigDecimal fee;
    private LegStatus status;
    private Instant startedAt;
    private Instant handedOverAt;
    private Instant completedAt;
    private String handoverNote;
    private Boolean isCurrentLeg;
    private Boolean canHandover;
    private Boolean canReceiveHandover;
}
