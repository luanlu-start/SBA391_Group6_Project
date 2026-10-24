package com.fptpost.modules.payment.model;

/**
 * Trạng thái của khoản tiền ký quỹ (Escrow).
 */
public enum EscrowStatus {
    /**
     * Tiền đang được giam giữ an toàn bởi hệ thống.
     */
    HELD,

    /**
     * Tiền đã giải ngân cho người giao hàng khi đơn hoàn thành (UC-36).
     */
    RELEASED,

    /**
     * Tiền đã hoàn lại cho người gửi khi đơn bị hủy hoặc sự cố (UC-37).
     */
    REFUNDED
}
