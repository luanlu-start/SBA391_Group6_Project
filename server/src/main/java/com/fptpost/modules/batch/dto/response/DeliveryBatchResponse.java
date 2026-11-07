package com.fptpost.modules.batch.dto.response;

import com.fptpost.modules.batch.model.DeliveryBatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryBatchResponse {

    private Long id;

    private String batchCode;

    private String batchName;

    private DeliveryBatchStatus status;

    private Integer totalOrders;

    private BigDecimal totalWeightKg;

    private BigDecimal totalDistanceKm;

    private Integer estimatedDurationMinutes;

    private String notes;

    private Long tripId;

    private String tripOrigin;

    private String tripDestination;

    private List<BatchStopResponse> stops;

    private List<BatchOrderSummaryResponse> orders;

    private Instant createdAt;

    private Instant updatedAt;
}
