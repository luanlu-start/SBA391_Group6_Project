package com.fptpost.modules.payment.model;

/**
 * Purpose of VNPay payment transaction.
 */
public enum PaymentPurpose {
    /**
     * Nạp tiền vào ví người dùng (UC-34).
     */
    WALLET_TOPUP,

    /**
     * Thanh toán ký quỹ cho đơn hàng (UC-35).
     */
    ORDER_ESCROW
}
