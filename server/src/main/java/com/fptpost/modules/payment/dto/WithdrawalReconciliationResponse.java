package com.fptpost.modules.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalReconciliationResponse {
    private long walletAvailable;
    private long walletPendingWithdraw;
    private long walletEscrowHeld;
    private long paidCount;
    private long paidTotal;
    private long unsuccessfulCount;
    private Instant lastPaidAt;
}
