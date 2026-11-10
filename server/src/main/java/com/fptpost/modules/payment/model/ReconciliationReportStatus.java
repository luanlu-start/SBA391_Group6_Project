package com.fptpost.modules.payment.model;

/**
 * Trạng thái báo cáo đối soát giao dịch hằng ngày (UC-43, DB-032).
 */
public enum ReconciliationReportStatus {
    MATCHED,
    MISMATCH_FOUND,
    RESOLVED
}
