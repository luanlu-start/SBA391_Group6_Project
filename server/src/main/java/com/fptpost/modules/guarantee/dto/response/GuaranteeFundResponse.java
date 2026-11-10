package com.fptpost.modules.guarantee.dto.response;

import com.fptpost.modules.payment.model.LedgerTransactionType;

import java.time.Instant;
import java.util.List;

/** Số dư, khoản chờ duyệt, khoản xếp hàng và biến động gần đây của Quỹ bảo đảm (API-129). */
public record GuaranteeFundResponse(
        long balance,
        long pendingCount,
        long pendingAmount,
        long queuedCount,
        long queuedAmount,
        long paidCount,
        long paidAmount,
        List<Movement> movements) {

    public record Movement(String transactionCode, LedgerTransactionType type, long amount, String description, Instant createdAt) {
    }
}
