package com.fptpost.modules.payment.model;

/**
 * Loại sai lệch phát hiện khi đối soát giao dịch hằng ngày (UC-43).
 */
public enum ReconciliationDiscrepancyType {
    NONE,
    MISSING_LEDGER,
    MISSING_PAYMENT,
    AMOUNT_MISMATCH,
    STALE_PENDING
}
