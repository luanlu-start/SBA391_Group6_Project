package com.fptpost.modules.payment.model;

/**
 * Loại nghiệp vụ ghi sổ cái (Ledger Transaction Type).
 */
public enum LedgerTransactionType {
    /**
     * Nạp tiền vào ví người dùng (UC-34).
     */
    WALLET_TOPUP,

    /**
     * Giam giữ tiền ký quỹ đơn hàng (UC-35).
     */
    ORDER_ESCROW_HOLD,

    /**
     * Giải ngân tiền ký quỹ cho shipper (UC-36).
     */
    ESCROW_RELEASE,

    /**
     * Hoàn tiền ký quỹ cho người gửi (UC-37).
     */
    ESCROW_REFUND
}
