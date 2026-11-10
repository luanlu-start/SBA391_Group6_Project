package com.fptpost.modules.payment.model;

/**
 * Trạng thái từng dòng đối soát giao dịch (UC-43, DB-033).
 */
public enum ReconciliationItemStatus {
    MATCHED,
    MISMATCH,
    ADJUSTED
}
