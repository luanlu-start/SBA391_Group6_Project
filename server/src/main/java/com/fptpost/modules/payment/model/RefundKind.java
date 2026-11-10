package com.fptpost.modules.payment.model;

/**
 * Phân loại hoàn tiền (UC-37, DB-030).
 */
public enum RefundKind {
    /**
     * Hoàn toàn bộ 100% số tiền ký quỹ.
     */
    FULL,

    /**
     * Hoàn một phần số tiền ký quỹ (sau khi khấu trừ phí hủy đơn 10% hoặc chia ký quỹ).
     */
    PARTIAL
}
