package com.fptpost.modules.batch.dto.response;

import com.fptpost.modules.batch.model.BatchStopStatus;
import com.fptpost.modules.batch.model.BatchStopType;
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
public class BatchStopResponse {

    private Long id;

    private Long orderId;

    private String orderCode;

    private Integer stopOrder;

    private BatchStopType stopType;

    private String address;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String contactName;

    private String contactPhone;

    private String itemDescription;

    private BigDecimal weightKg;

    private BigDecimal distanceFromPrevKm;

    private Integer estimatedArrivalMinutes;

    private BatchStopStatus status;

    private Instant completedAt;

    private String stopKey; // ví dụ: "PICKUP_12" hoặc "DELIVERY_12"
}
