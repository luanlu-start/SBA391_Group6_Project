package com.fptpost.modules.payment.model;

/**
 * Trạng thái yêu cầu hoàn tiền về nguồn VNPay (UC-37, DB-030).
 */
public enum RefundStatus {
    PENDING,
    SUCCESS,
    FAILED
}
