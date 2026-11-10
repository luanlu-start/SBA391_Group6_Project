package com.fptpost.modules.payment.dto;

import com.fptpost.modules.payment.model.ReconciliationDiscrepancyType;
import com.fptpost.modules.payment.model.ReconciliationItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response chi tiết một dòng đối soát giao dịch (UC-43, DB-033).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dòng chi tiết đối soát giao dịch")
public class ReconciliationItemResponse {

    private Long id;
    private Long reportId;
    private String txnRef;
    private String kind;
    private Long internalAmount;
    private Long vnpayAmount;
    private Long differenceAmount;
    private ReconciliationItemStatus status;
    private ReconciliationDiscrepancyType discrepancyType;
    private String note;
    private String adjustmentTxnCode;
    private Long adjustedBy;
    private Instant adjustedAt;
    private Instant createdAt;
}
