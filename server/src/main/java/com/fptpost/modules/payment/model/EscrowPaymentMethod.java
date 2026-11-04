package com.fptpost.modules.payment.model;

/**
 * Phương thức thanh toán ký quỹ đơn hàng.
 */
public enum EscrowPaymentMethod {
    /**
     * Trừ trực tiếp từ số dư ví nội bộ FPTPost.
     */
    WALLET,

    /**
     * Thanh toán trực tuyến qua cổng VNPay.
     */
    VNPAY
}
