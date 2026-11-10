package com.fptpost.modules.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierEarningsResponse {
    private String period;
    private LocalDate fromDate;
    private LocalDate toDate;
    private int completedOrdersCount;
    private long grossEarnings;
    private BigDecimal commissionRate;
    private long commissionAmount;
    private long deductionAmount;
    private long netEarnings;
    private long todayNetEarnings;
    private long pendingEscrowAmount;
    private List<CourierEarningPointDto> chartPoints;
    private List<CourierEarningReceiptDto> receipts;
}
